package io.github.shahart.twa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Checks the Android wrapper's routing and site association configuration. */
public class TwaConfigurationTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private final File projectDir = new File(System.getProperty("projectDir"));

    @Test
    public void launchAndDeepLinkConfigMatchTheTwaManifest() throws Exception {
        String gradle = read("app/build.gradle");
        String twaJson = read("twa-manifest.json");
        String host = gradleValue(gradle, "hostName");
        String launchPath = gradleValue(gradle, "launchUrl");

        assertEquals(host, jsonValue(twaJson, "host"));
        assertEquals(launchPath, jsonValue(twaJson, "startUrl"));
        assertTrue("launch URL must be an absolute path", launchPath.startsWith("/"));

        Element launcher = launcherActivity(parseManifest());
        assertEquals("@string/launchUrl",
                metadataValue(launcher, "android.support.customtabs.trusted.DEFAULT_URL"));
        assertTrue("Gradle must generate the launch URL resource used by the manifest",
                gradle.contains("resValue \"string\", \"launchUrl\", launchUrl"));

        Element linkFilter = verifiedWebLinkFilter(launcher);
        assertTrue(hasAction(linkFilter, "android.intent.action.VIEW"));
        assertTrue(hasCategory(linkFilter, "android.intent.category.DEFAULT"));
        assertTrue(hasCategory(linkFilter, "android.intent.category.BROWSABLE"));

        Element data = (Element) linkFilter.getElementsByTagName("data").item(0);
        assertEquals("https", androidAttribute(data, "scheme"));
        assertEquals("@string/hostName", androidAttribute(data, "host"));
        assertEquals(launchPath, androidAttribute(data, "pathPrefix"));
    }

    @Test
    public void assetStatementAssociatesTheConfiguredWebOrigin() throws Exception {
        String gradle = read("app/build.gradle");
        String host = gradleValue(gradle, "hostName");
        Document manifest = parseManifest();
        NodeList metadata = manifest.getElementsByTagName("meta-data");
        boolean referencesAssetStatements = false;
        for (int i = 0; i < metadata.getLength(); i++) {
            Element element = (Element) metadata.item(i);
            if ("asset_statements".equals(androidAttribute(element, "name"))
                    && "@string/assetStatements".equals(androidAttribute(element, "resource"))) {
                referencesAssetStatements = true;
                break;
            }
        }
        assertTrue("manifest must include the Digital Asset Links statement", referencesAssetStatements);

        String strings = read("app/src/main/res/values/strings.xml");
        assertTrue("Digital Asset Links site must match the configured host",
                strings.contains("https://" + host));
        assertTrue(strings.contains("delegate_permission/common.handle_all_urls"));
    }

    @Test
    public void fallbackAndNotificationDelegationAreDeclaredConsistently() throws Exception {
        String gradle = read("app/build.gradle");
        String twaJson = read("twa-manifest.json");
        String fallback = gradleValue(gradle, "fallbackType");
        assertEquals(fallback, jsonValue(twaJson, "fallbackType"));
        assertTrue(gradle.contains("resValue \"string\", \"fallbackType\", twaManifest.fallbackType"));

        Document manifest = parseManifest();
        Element launcher = launcherActivity(manifest);
        assertEquals("@string/fallbackType",
                metadataValue(launcher, "android.support.customtabs.trusted.FALLBACK_STRATEGY"));
        assertTrue(twaJson.contains("\"enableNotifications\": true"));
        assertTrue(gradle.contains("enableNotifications: true"));
        assertTrue(gradle.contains("resValue \"bool\", \"enableNotification\", twaManifest.enableNotifications.toString()"));

        NodeList services = manifest.getElementsByTagName("service");
        boolean delegatesNotifications = false;
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            if (".DelegationService".equals(androidAttribute(service, "name"))
                    && "@bool/enableNotification".equals(androidAttribute(service, "enabled"))
                    && hasAction(service, "android.support.customtabs.trusted.TRUSTED_WEB_ACTIVITY_SERVICE")) {
                delegatesNotifications = true;
                break;
            }
        }
        assertTrue("TWA notification delegation service must be enabled", delegatesNotifications);
        assertTrue("notification permission must be declared",
                hasPermission(manifest, "android.permission.POST_NOTIFICATIONS"));
    }

    private Document parseManifest() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new File(projectDir, "app/src/main/AndroidManifest.xml"));
    }

    private Element launcherActivity(Document manifest) {
        NodeList activities = manifest.getElementsByTagName("activity");
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if ("LauncherActivity".equals(androidAttribute(activity, "name"))) return activity;
        }
        throw new AssertionError("LauncherActivity is missing from AndroidManifest.xml");
    }

    private Element verifiedWebLinkFilter(Element launcher) {
        NodeList filters = launcher.getElementsByTagName("intent-filter");
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            if ("true".equals(androidAttribute(filter, "autoVerify"))
                    && filter.getElementsByTagName("data").getLength() > 0) return filter;
        }
        throw new AssertionError("verified HTTPS link intent filter is missing");
    }

    private boolean hasAction(Element filter, String expected) {
        NodeList actions = filter.getElementsByTagName("action");
        for (int i = 0; i < actions.getLength(); i++) {
            if (expected.equals(androidAttribute((Element) actions.item(i), "name"))) return true;
        }
        return false;
    }

    private boolean hasCategory(Element filter, String expected) {
        NodeList categories = filter.getElementsByTagName("category");
        for (int i = 0; i < categories.getLength(); i++) {
            if (expected.equals(androidAttribute((Element) categories.item(i), "name"))) return true;
        }
        return false;
    }

    private boolean hasPermission(Document manifest, String expected) {
        NodeList permissions = manifest.getElementsByTagName("uses-permission");
        for (int i = 0; i < permissions.getLength(); i++) {
            if (expected.equals(androidAttribute((Element) permissions.item(i), "name"))) return true;
        }
        return false;
    }

    private String metadataValue(Element parent, String name) {
        NodeList metadata = parent.getElementsByTagName("meta-data");
        for (int i = 0; i < metadata.getLength(); i++) {
            Element element = (Element) metadata.item(i);
            if (name.equals(androidAttribute(element, "name"))) {
                return androidAttribute(element, "value");
            }
        }
        throw new AssertionError("Missing metadata " + name);
    }

    private String androidAttribute(Element element, String name) {
        return element.getAttributeNS(ANDROID_NS, name);
    }

    private String gradleValue(String gradle, String key) {
        Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(key)
                + ":\\s*'([^']*)'").matcher(gradle);
        if (!matcher.find()) throw new AssertionError("Missing " + key + " in app/build.gradle");
        return matcher.group(1);
    }

    private String jsonValue(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        if (!matcher.find()) throw new AssertionError("Missing " + key + " in twa-manifest.json");
        return matcher.group(1);
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(projectDir, path).toPath()), StandardCharsets.UTF_8);
    }
}
