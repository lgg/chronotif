import { readFileSync } from "node:fs";
import { defineConfig } from "vite";

const REPO = "nagarete/chronotif";

// The download buttons link straight to the newest release's APK. It's resolved at build time
// (so visitors' browsers never call GitHub's API) with the same rule as the in-app updater:
// the newest non-draft release that ships an .apk, pre-releases included. Rebuild after
// publishing a release to pick it up.
//
// If GitHub can't be reached (or CHRONOTIF_OFFLINE=1), fall back to the app's versionName.
// CI only publishes tags matching it, so that URL is right once its release is out.

const parse = (tag) => tag.replace(/^v/, "").match(/^(\d+)\.(\d+)\.(\d+)$/)?.slice(1).map(Number);
const newer = (a, b) => {
  for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return a[i] > b[i];
  return false;
};

async function newestRelease() {
  const res = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=20`, {
    headers: { Accept: "application/vnd.github+json", "User-Agent": "chronotif-website-build" },
    signal: AbortSignal.timeout(10_000),
  });
  if (!res.ok) throw new Error(`GitHub API returned ${res.status}`);
  let best = null;
  for (const rel of await res.json()) {
    const v = parse(rel.tag_name ?? "");
    const apk = rel.assets?.find((a) => a.name.endsWith(".apk"));
    if (rel.draft || !v || !apk) continue;
    if (!best || newer(v, best.v)) best = { v, rel, apk };
  }
  if (!best) throw new Error("no published release with an APK");
  return {
    tag: best.rel.tag_name,
    prerelease: best.rel.prerelease,
    releaseUrl: best.rel.html_url,
    apkName: best.apk.name,
    apkUrl: best.apk.browser_download_url,
    apkSize: `~${Math.round(best.apk.size / 10_240) * 10} KB`,
  };
}

function fromVersionName() {
  const gradle = readFileSync(new URL("../app/build.gradle.kts", import.meta.url), "utf8");
  const version = gradle.match(/versionName\s*=\s*"(\d+\.\d+\.\d+)"/)?.[1];
  if (!version) throw new Error('Could not find versionName = "x.y.z" in app/build.gradle.kts');
  const tag = `v${version}`;
  return {
    tag,
    prerelease: version.startsWith("0."), // CI publishes 0.x tags as pre-releases
    releaseUrl: `https://github.com/${REPO}/releases/tag/${tag}`,
    apkName: `chronotif-${tag}.apk`,
    apkUrl: `https://github.com/${REPO}/releases/download/${tag}/chronotif-${tag}.apk`,
    apkSize: "~140 KB", // the README's figure; only used when GitHub can't be reached
  };
}

async function release() {
  if (process.env.CHRONOTIF_OFFLINE !== "1") {
    try {
      return await newestRelease();
    } catch (e) {
      console.warn(`[chronotif] Couldn't fetch releases (${e.message}); using versionName instead.`);
    }
  }
  return fromVersionName();
}

export default defineConfig(async () => {
  const r = await release();
  console.log(`[chronotif] Download links → ${r.apkUrl}`);
  const vars = {
    VERSION: r.tag.replace(/^v/, ""),
    TAG: r.tag,
    RELEASE_URL: r.releaseUrl,
    APK_NAME: r.apkName,
    APK_URL: r.apkUrl,
    APK_SIZE: r.apkSize.replace(" ", "&nbsp;"),
    RELEASE_KIND: r.prerelease ? "pre-release" : "release",
  };
  return {
    plugins: [
      {
        name: "chronotif-release",
        transformIndexHtml: (html) =>
          html.replace(/%CHRONOTIF_(\w+)%/g, (m, key) => {
            if (!(key in vars)) throw new Error(`Unknown placeholder ${m}`);
            return vars[key];
          }),
      },
    ],
  };
});
