// Renders scripts/og.html to public/og.png (the social sharing image) with headless Chromium.
// Usage: npm run og   (set CHROME=/path/to/chrome if `chromium` isn't on PATH)
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("..", import.meta.url));
const chrome = process.env.CHROME || "chromium";

execFileSync(chrome, [
  "--headless=new",
  "--disable-gpu",
  "--hide-scrollbars",
  "--allow-file-access-from-files",
  "--window-size=1200,630",
  "--virtual-time-budget=2000",
  `--screenshot=${root}public/og.png`,
  `file://${root}scripts/og.html`,
], { stdio: "inherit" });
console.log("wrote public/og.png");
