export const TURN_MS = 7_000;
export const EMOJIS = Object.freeze([
  "😀", "😎", "🥳", "🤩", "😂", "😊", "🦊", "🐼", "🐸", "🦁", "🐵", "🐙",
  "🦄", "🐲", "🦖", "🐝", "🦋", "🐳", "🚀", "⚡", "🔥", "🌈", "⭐", "🌙",
]);

const ENGLISH_NUMBERS = Object.freeze({
  zero: 0, one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, eight: 8, nine: 9,
  ten: 10, eleven: 11, twelve: 12, thirteen: 13, fourteen: 14, fifteen: 15, sixteen: 16,
  seventeen: 17, eighteen: 18, nineteen: 19, twenty: 20, thirty: 30, forty: 40, fifty: 50,
  sixty: 60, seventy: 70, eighty: 80, ninety: 90, hundred: 100, thousand: 1_000, million: 1_000_000, billion: 1_000_000_000,
});

const HEBREW_NUMBERS = Object.freeze({
  "אפס": 0, "אחד": 1, "אחת": 1, "ואן": 1, "וואן": 1, "שניים": 2, "שתיים": 2, "שני": 2, "שתי": 2,
  "שלוש": 3, "שלושה": 3, "ארבע": 4, "ארבעה": 4, "חמש": 5, "חמישה": 5,
  "שש": 6, "שישה": 6, "שבע": 7, "שבעה": 7, "שמונה": 8, "תשע": 9, "תשעה": 9,
  "עשר": 10, "עשרה": 10, "אחד עשר": 11, "אחת עשרה": 11, "עשרים": 20,
  "שלושים": 30, "ארבעים": 40, "חמישים": 50, "שישים": 60, "שבעים": 70,
  "שמונים": 80, "תשעים": 90, "מאה": 100, "מאתיים": 200, "שלוש מאות": 300, "ארבע מאות": 400, "חמש מאות": 500,
  "שש מאות": 600, "שבע מאות": 700, "שמונה מאות": 800, "תשע מאות": 900,
  "אלף": 1_000, "אלפיים": 2_000, "שלושת אלפים": 3_000, "ארבעת אלפים": 4_000, "חמשת אלפים": 5_000,
  "ששת אלפים": 6_000, "שבעת אלפים": 7_000, "שמונת אלפים": 8_000, "תשעת אלפים": 9_000, "מיליון": 1_000_000, "מיליארד": 1_000_000_000,
});

export function isBoom(number) {
  return number % 7 === 0 || String(number).includes("7");
}

export function hasMultiplePlayers(connectedPlayers) {
  return connectedPlayers > 1;
}

export function playerIndicatorsMarkup({ connectedPlayers, currentEmoji, yourEmoji, currentLabel, youLabel, active = true }) {
  if (!active || !hasMultiplePlayers(connectedPlayers)) return "";
  const escape = (value) => String(value).replace(/[&<>"']/g, (char) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  })[char]);
  return `<div class="player-corner" aria-label="${escape(currentLabel)}: ${escape(currentEmoji)}; ${escape(youLabel)}: ${escape(yourEmoji)}">
    <span class="player-corner-item"><span class="player-corner-emoji" aria-hidden="true">${escape(currentEmoji)}</span><span>${escape(currentLabel)}</span></span>
    <span class="player-corner-item"><span class="player-corner-emoji" aria-hidden="true">${escape(yourEmoji)}</span><span>${escape(youLabel)}</span></span>
  </div>`;
}

export function remainingSeconds(deadline, serverOffset = 0, now = Date.now()) {
  if (deadline == null) return 0;
  return Math.max(0, Math.min(7, Math.ceil((deadline - (now + serverOffset)) / 1_000)));
}

export function remainingProgress(deadline, serverOffset = 0, now = Date.now()) {
  if (deadline == null) return 0;
  return Math.max(0, Math.min(1, (deadline - (now + serverOffset)) / TURN_MS));
}

export function responseMillis(deadline, serverOffset = 0, now = Date.now()) {
  if (deadline == null) return 0;
  const elapsed = now + serverOffset - (deadline - TURN_MS);
  return Math.max(0, Math.min(TURN_MS, Math.round(elapsed)));
}

export function answerMessage(kind, turnId, number, responseTimeMillis) {
  const answer = kind === "boom"
    ? { type: "boom" }
    : kind === "invalid"
      ? { type: "invalid" }
      : { type: "number", value: number };
  return {
    type: "answer",
    turnId,
    responseTimeMillis: Math.max(0, Math.min(TURN_MS, Math.round(responseTimeMillis))),
    answer,
  };
}

export function gameSocketUrl(location) {
  const workerHost = "global-seven-boom.lat-shahar.workers.dev";
  const localWorker = ["localhost", "127.0.0.1", "::1"].includes(location.hostname) && location.port === "8787";
  const sameOriginWorker = location.hostname === workerHost || localWorker;
  const scheme = location.protocol === "https:" ? "wss:" : "ws:";
  const host = sameOriginWorker ? location.host : workerHost;
  return `${scheme}//${host}/game`;
}

export function recognizedAnswer(text, number) {
  const normalized = String(text).toLocaleLowerCase().normalize("NFKC")
    .replace(/[^\p{L}\p{N}\s-]/gu, " ").replace(/\s+/g, " ").trim();
  const words = normalized.replace(/-/g, " ").split(" ").filter(Boolean);
  if (isBoom(number)) return words.length > 0 && words.every((word) => word === "boom" || word === "בום") ? "boom" : "invalid";
  return parseSpokenNumber(normalized) === number ? "number" : "invalid";
}

function parseSpokenNumber(value) {
  const direct = Number(value);
  if (Number.isSafeInteger(direct) && direct > 0) return direct;
  const directHebrew = HEBREW_NUMBERS[value];
  if (directHebrew !== undefined) return directHebrew;

  const words = value.replace(/-/g, " ").split(/\s+/).filter(Boolean)
    .map((word) => word.startsWith("ו") && word.length > 1 ? word.slice(1) : word);
  if (!words.length) return null;
  if (words.length === 1) return ENGLISH_NUMBERS[words[0]] ?? HEBREW_NUMBERS[words[0]] ?? null;

  if (words.length === 2) {
    const [first, second] = words;
    const englishTens = ENGLISH_NUMBERS[first];
    const englishUnit = ENGLISH_NUMBERS[second];
    if (englishTens >= 20 && englishTens < 100 && englishUnit > 0 && englishUnit < 10) return englishTens + englishUnit;

    const hebrewTens = HEBREW_NUMBERS[first];
    const hebrewUnit = HEBREW_NUMBERS[second];
    if (hebrewTens >= 20 && hebrewTens < 100 && hebrewUnit > 0 && hebrewUnit < 10) return hebrewTens + hebrewUnit;

    const joined = words.join(" ");
    if (joined in HEBREW_NUMBERS) return HEBREW_NUMBERS[joined];
    if (ENGLISH_NUMBERS[first] > 0 && ENGLISH_NUMBERS[first] < 10 && second === "hundred") return ENGLISH_NUMBERS[first] * 100;
  }

  if (words.length > 1 && words.every((word) => word in ENGLISH_NUMBERS && ENGLISH_NUMBERS[word] >= 0 && ENGLISH_NUMBERS[word] < 10)) {
    return Number(words.map((word) => ENGLISH_NUMBERS[word]).join(""));
  }
  return null;
}
