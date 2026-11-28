/**
 * NeliTv Backend Proxy Endpoint for YouTube Video IDs (Node.js / Express)
 *
 * Mount on https://nelitv.online as:
 *   GET /api/v1/youtube/video?id=<11-char-youtube-video-id>
 *
 * Environment variables on the nelitv.online server ONLY (never inside the Android APK):
 *   YOUTUBE_API_KEY=<your YouTube Data API v3 server key>
 *   NELITV_API_KEY=<optional shared app key checked against X-Neli-Api-Key>
 *
 * Scope:
 *   - Resolves ONLY a single 11-character YouTube video ID used by existing in-app movies/episodes.
 *   - No open YouTube search or playlist crawling exposed.
 *   - 15-minute server-side cache protects API quota.
 */

const YOUTUBE_ID_REGEX = /^[A-Za-z0-9_-]{11}$/;
const CACHE_TTL_MS = 15 * 60 * 1000;
const videoCache = new Map();

function formatIsoDuration(iso = "") {
  const m = /^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$/.exec(String(iso).trim());
  if (!m) return "";
  const h = Number(m[1] || 0);
  const min = Number(m[2] || 0);
  const s = Number(m[3] || 0);
  if (h > 0 && min > 0) return `${h}h ${min}m`;
  if (h > 0) return `${h}h`;
  if (min > 0) return `${min}m`;
  if (s > 0) return `${s}s`;
  return "";
}

function pickThumbnail(thumbnails, videoId) {
  for (const key of ["maxres", "standard", "high", "medium", "default"]) {
    const url = thumbnails?.[key]?.url;
    if (url) return url;
  }
  return `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`;
}

/**
 * Express route handler: app.get('/api/v1/youtube/video', handleNeliYoutubeVideoProxy);
 */
async function handleNeliYoutubeVideoProxy(req, res) {
  try {
    const expectedKey = (process.env.NELITV_API_KEY || "").trim();
    if (expectedKey) {
      const providedKey = String(req.headers["x-neli-api-key"] || "").trim();
      if (providedKey !== expectedKey) {
        return res.status(401).json({ ok: false, error: "Unauthorized" });
      }
    }

    const videoId = String(req.query.id || "").trim();
    if (!YOUTUBE_ID_REGEX.test(videoId)) {
      return res.status(400).json({ ok: false, error: "Invalid YouTube video ID" });
    }

    const cached = videoCache.get(videoId);
    if (cached && Date.now() - cached.cachedAt < CACHE_TTL_MS) {
      return res.json(cached.payload);
    }

    const ytKey = (process.env.YOUTUBE_API_KEY || "").trim();
    if (!ytKey) {
      return res.status(503).json({ ok: false, error: "Server YOUTUBE_API_KEY not configured" });
    }

    const apiUrl = new URL("https://www.googleapis.com/youtube/v3/videos");
    apiUrl.searchParams.set("part", "snippet,contentDetails,status,statistics");
    apiUrl.searchParams.set("id", videoId);
    apiUrl.searchParams.set("key", ytKey);

    const upstream = await fetch(apiUrl.toString(), {
      headers: { Accept: "application/json" }
    });
    if (!upstream.ok) {
      return res.status(502).json({ ok: false, error: `Upstream YouTube API ${upstream.status}` });
    }

    const data = await upstream.json();
    const item = Array.isArray(data.items) ? data.items[0] : null;
    if (!item) {
      const unavailable = {
        ok: true,
        videoId,
        title: "",
        channelTitle: "",
        thumbnailUrl: `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`,
        durationLabel: "",
        viewCount: 0,
        isAvailable: false,
        isEmbeddable: false,
        embedUrl: `https://baynith-tv.base44.app/embed-video/${videoId}`
      };
      videoCache.set(videoId, { payload: unavailable, cachedAt: Date.now() });
      return res.json(unavailable);
    }

    const snippet = item.snippet || {};
    const status = item.status || {};
    const contentDetails = item.contentDetails || {};
    const stats = item.statistics || {};

    const privacy = String(status.privacyStatus || "public").toLowerCase();
    const upload = String(status.uploadStatus || "processed").toLowerCase();
    const isAvailable =
      privacy !== "private" &&
      upload !== "rejected" &&
      upload !== "deleted" &&
      upload !== "failed";

    const payload = {
      ok: true,
      videoId,
      title: String(snippet.title || "").trim(),
      channelTitle: String(snippet.channelTitle || "").trim(),
      thumbnailUrl: pickThumbnail(snippet.thumbnails, videoId),
      durationLabel: formatIsoDuration(contentDetails.duration || ""),
      viewCount: Number(stats.viewCount || 0) || 0,
      isAvailable,
      isEmbeddable: status.embeddable !== false,
      embedUrl: `https://baynith-tv.base44.app/embed-video/${videoId}`
    };

    videoCache.set(videoId, { payload, cachedAt: Date.now() });
    return res.json(payload);
  } catch (err) {
    return res.status(500).json({ ok: false, error: "Proxy lookup failed" });
  }
}

module.exports = { handleNeliYoutubeVideoProxy };
