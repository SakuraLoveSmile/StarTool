function ut(i) {
  switch (i) {
    case "waiting_configuration":
      return "反馈已保存，等待管理员配置该软件。";
    case "waiting_source_confirmation":
      return "反馈已保存，等待管理员确认来源。";
    case "waiting_manual_archive":
      return "反馈已保存，等待管理员归档。";
    default:
      return null;
  }
}
class H extends Error {
  status;
  code;
  /** 429 daily_quota_exceeded 等错误体携带的同结构额度（若有）。 */
  quota;
  constructor(t, e, s, n) {
    super(s || e), this.name = "ApiError", this.status = t, this.code = e, this.quota = n;
  }
}
function at(i, t) {
  return `${i.replace(/\/+$/, "")}${t}`;
}
function Ce(i) {
  const t = new Uint8Array(i);
  return crypto.getRandomValues(t), Array.from(t, (e) => e.toString(16).padStart(2, "0")).join("");
}
function ft() {
  if (typeof crypto.randomUUID == "function") return crypto.randomUUID();
  const i = Ce(16).split("");
  i[12] = "4";
  const t = parseInt(i[16] ?? "0", 16);
  i[16] = (t & 3 | 8).toString(16);
  const e = i.join("");
  return `${e.slice(0, 8)}-${e.slice(8, 12)}-${e.slice(12, 16)}-${e.slice(16, 20)}-${e.slice(20)}`;
}
function pe(i) {
  if (typeof i != "object" || i === null) return;
  const t = i, e = {};
  return t.dialogue === !0 && (e.dialogue = !0), typeof t.images == "number" && Number.isFinite(t.images) && t.images >= 1 && (e.images = Math.min(5, Math.floor(t.images))), e;
}
async function Re(i) {
  const t = await i.arrayBuffer(), e = await crypto.subtle.digest("SHA-256", t);
  return Array.from(new Uint8Array(e), (s) => s.toString(16).padStart(2, "0")).join("");
}
function ot(i) {
  if (typeof i != "object" || i === null) return;
  const t = i;
  if (t.unlimited === !0)
    return typeof t.used != "number" || typeof t.resetAt != "string" ? void 0 : { unlimited: !0, used: t.used, resetAt: t.resetAt };
  if (!(typeof t.dailyLimit != "number" || typeof t.used != "number" || typeof t.remaining != "number" || typeof t.resetAt != "string"))
    return { dailyLimit: t.dailyLimit, used: t.used, remaining: t.remaining, resetAt: t.resetAt };
}
function Lt(i, t) {
  const e = t?.error;
  throw new H(
    i,
    e?.code ?? `http_${i}`,
    e?.message ?? `HTTP ${i}`,
    ot(t?.quota)
  );
}
const Ie = 2e4;
async function V(i, t, e, s = {}) {
  const n = {};
  s.token && (n.Authorization = `Bearer ${s.token}`);
  let r;
  s.body !== void 0 && (n["Content-Type"] = "application/json", r = JSON.stringify(s.body));
  const a = typeof s.timeoutMs == "number" ? new AbortController() : null, o = a ? setTimeout(() => a.abort(), s.timeoutMs) : null;
  try {
    const h = await (s.fetchFn ?? fetch)(at(i, e), {
      method: t,
      headers: n,
      body: r,
      credentials: "omit",
      ...a ? { signal: a.signal } : {}
    });
    if (h.status === 204) return;
    let d = null;
    try {
      d = await h.json();
    } catch {
      d = null;
    }
    return h.ok || Lt(h.status, d), d;
  } finally {
    o !== null && clearTimeout(o);
  }
}
async function Me(i, t, e = {}) {
  const s = await V(i, "POST", "/api/auth/login", { body: t, fetchFn: e.fetchFn });
  return { ...s, quota: ot(s.quota) };
}
async function Te(i, t, e = {}) {
  const s = await V(i, "GET", "/api/auth/session", {
    token: t,
    timeoutMs: Ie,
    fetchFn: e.fetchFn
  });
  return { ...s, quota: ot(s.quota) };
}
async function Pe(i, t = {}) {
  const e = await V(i, "GET", "/api/features", {
    fetchFn: t.fetchFn
  });
  return pe(e) ?? {};
}
function Fe(i) {
  const t = (i.type || "").toLowerCase();
  return t === "image/jpeg" || t === "image/webp" ? t : "image/png";
}
async function be(i) {
  const t = [];
  for (const e of i)
    t.push({
      id: e.id,
      source: e.source,
      filename: e.filename,
      sha256: await Re(e.blob),
      mime: Fe(e.blob)
    });
  return t;
}
async function ze(i, t, e, s = {}) {
  const n = Array.isArray(e.logs) && e.logs.length > 0, r = Array.isArray(e.images) && e.images.length > 0;
  if (r && e.screenshot)
    throw new H(0, "invalid_request", "images 与 screenshot 不能同时提交");
  if (e.screenshot || n || r) {
    const o = new FormData(), h = {
      idempotencyKey: e.idempotencyKey,
      appId: e.appId,
      text: e.text
    };
    e.appName && (h.appName = e.appName), e.context && (h.context = e.context), e.capture && (h.capture = e.capture), n && e.logs && (h.logs = e.logs.map((b) => ({
      filename: b.filename,
      source: b.source,
      ...b.sha256 ? { sha256: b.sha256 } : {}
    })));
    const d = r && e.images ? await be(e.images) : [];
    if (d.length > 0 && (h.images = d), o.append("metadata", JSON.stringify(h)), e.screenshot && o.append("screenshot", e.screenshot, "screenshot.png"), r && e.images)
      for (let b = 0; b < e.images.length; b++) {
        const m = e.images[b];
        o.append("images", m.blob, m.filename || `image-${b + 1}.png`);
      }
    if (n && e.logs)
      for (const b of e.logs)
        o.append("logs", b.blob, b.filename);
    const c = {};
    t && (c.Authorization = `Bearer ${t}`);
    const u = await (s.fetchFn ?? fetch)(at(i, "/api/feedback"), {
      method: "POST",
      headers: c,
      body: o,
      credentials: "omit"
    });
    if (u.status === 204) return;
    let f = null;
    try {
      f = await u.json();
    } catch {
      f = null;
    }
    u.ok || Lt(u.status, f);
    const x = f;
    return { ...x, quota: ot(x.quota) };
  }
  const a = await V(i, "POST", "/api/feedback", {
    body: e,
    token: t,
    fetchFn: s.fetchFn
  });
  return { ...a, quota: ot(a.quota) };
}
function Wt(i, t, e, s = {}) {
  return V(i, "GET", `/api/feedback/${encodeURIComponent(e)}`, {
    token: t,
    fetchFn: s.fetchFn
  });
}
function Ue(i, t, e, s = {}) {
  const n = e ? `?appId=${encodeURIComponent(e)}` : "";
  return V(i, "GET", `/api/feedback/unread-summary${n}`, {
    token: t,
    fetchFn: s.fetchFn
  });
}
function Oe(i, t, e = {}) {
  const s = new URLSearchParams();
  return e.page && s.set("page", String(e.page)), e.limit && s.set("limit", String(e.limit)), e.appId && s.set("appId", e.appId), e.issueStatus && s.set("issueStatus", e.issueStatus), V(i, "GET", `/api/feedback/mine?${s.toString()}`, {
    token: t,
    fetchFn: e.fetchFn
  });
}
function qe(i, t, e, s = {}) {
  return V(
    i,
    "GET",
    `/api/feedback/${encodeURIComponent(e)}/dialogue`,
    { token: t, fetchFn: s.fetchFn }
  );
}
async function De(i, t, e, s, n = {}) {
  const r = Array.isArray(s.images) && s.images.length > 0, a = !!(s.screenshot || r || s.logs && s.logs.length > 0);
  if (r && s.screenshot)
    throw new H(0, "invalid_request", "images 与 screenshot 不能同时提交");
  if (a) {
    const o = new FormData();
    if (r && s.images) {
      const u = { text: s.text };
      s.idempotencyKey && (u.idempotencyKey = s.idempotencyKey), u.images = await be(s.images), o.append("metadata", JSON.stringify(u));
      for (let f = 0; f < s.images.length; f++) {
        const x = s.images[f];
        o.append("images", x.blob, x.filename || `image-${f + 1}.png`);
      }
    } else
      o.append("text", s.text), s.idempotencyKey && o.append("idempotencyKey", s.idempotencyKey), s.screenshot && o.append("screenshot", s.screenshot, "screenshot.png");
    if (s.logs)
      for (const u of s.logs)
        o.append("logs", u, u.name || "log.txt");
    const h = { Authorization: `Bearer ${t}` }, d = await (n.fetchFn ?? fetch)(
      at(i, `/api/feedback/${encodeURIComponent(e)}/messages`),
      {
        method: "POST",
        headers: h,
        body: o,
        credentials: "omit"
      }
    );
    let c = null;
    try {
      c = await d.json();
    } catch {
      c = null;
    }
    return d.ok || Lt(d.status, c), c;
  }
  return V(
    i,
    "POST",
    `/api/feedback/${encodeURIComponent(e)}/messages`,
    {
      token: t,
      fetchFn: n.fetchFn,
      body: {
        text: s.text,
        idempotencyKey: s.idempotencyKey
      }
    }
  );
}
function He(i, t, e, s, n = {}) {
  return V(
    i,
    "POST",
    `/api/feedback/${encodeURIComponent(e)}/read`,
    {
      token: t,
      fetchFn: n.fetchFn,
      body: { lastReadSeq: s }
    }
  );
}
async function Kt(i, t, e, s = {}) {
  const n = await (s.fetchFn ?? fetch)(at(i, e), {
    method: "GET",
    headers: { Authorization: `Bearer ${t}` },
    credentials: "omit"
  });
  if (!n.ok) {
    let r = null;
    try {
      r = await n.json();
    } catch {
      r = null;
    }
    Lt(n.status, r);
  }
  return n.blob();
}
function gt(i) {
  return Array.from(i).length;
}
const Y = 1e4, J = 5, _e = 2048, Ne = 4e6, rt = 5 * 1024 * 1024, me = 3;
class G extends Error {
  reason;
  constructor(t, e) {
    super(e), this.name = "ImageInputError", this.reason = t;
  }
}
function $e(i) {
  return i.length >= 8 && i[0] === 137 && i[1] === 80 && i[2] === 78 && i[3] === 71 ? "image/png" : i.length >= 3 && i[0] === 255 && i[1] === 216 && i[2] === 255 ? "image/jpeg" : i.length >= 12 && i[0] === 82 && // R
  i[1] === 73 && // I
  i[2] === 70 && // F
  i[3] === 70 && // F
  i[8] === 87 && // W
  i[9] === 69 && // E
  i[10] === 66 && // B
  i[11] === 80 ? "image/webp" : null;
}
function Ve(i) {
  for (let t = 12; t + 8 <= Math.min(i.length, 256); ) {
    const e = String.fromCharCode(i[t], i[t + 1], i[t + 2], i[t + 3]), s = (i[t + 4] | i[t + 5] << 8 | i[t + 6] << 16 | i[t + 7] << 24) >>> 0;
    if (e === "VP8X" && s >= 1)
      return (i[t + 8] & 2) !== 0;
    if (e === "VP8 " || e === "VP8L") return !1;
    t += 8 + s + s % 2;
  }
  return !1;
}
const je = {
  "image/png": ".png",
  "image/jpeg": ".jpg",
  "image/webp": ".webp"
};
function pt(i, t, e) {
  const n = ((i || "").split(/[\\/]/).pop() ?? "").replace(/[\r\n]/g, "").trim() || e, r = n.lastIndexOf("."), a = r === -1 ? "" : n.slice(r).toLowerCase();
  return t === "image/png" && a === ".png" || t === "image/jpeg" && (a === ".jpg" || a === ".jpeg") || t === "image/webp" && a === ".webp" ? n : `${n.replace(/\.[^.]*$/, "")}${je[t]}`;
}
async function We(i) {
  const t = URL.createObjectURL(i);
  try {
    const e = new Image(), s = new Promise((o, h) => {
      e.onload = () => o(e), e.onerror = () => h(new G("decode_failed", "图片解码失败"));
    });
    e.src = t;
    const n = await s, r = n.naturalWidth || n.width, a = n.naturalHeight || n.height;
    if (r <= 0 || a <= 0) throw new G("decode_failed", "图片尺寸无效");
    return { width: r, height: a, source: n };
  } finally {
    URL.revokeObjectURL(t);
  }
}
async function vt(i, t = {}) {
  if (t.decode) return t.decode(i);
  if (typeof createImageBitmap == "function")
    try {
      const e = await createImageBitmap(i);
      if (e.width > 0 && e.height > 0)
        return { width: e.width, height: e.height, source: e, close: () => e.close() };
      e.close();
    } catch {
    }
  return We(i);
}
function et(i, t) {
  return new Promise((e) => {
    try {
      i.toBlob((s) => e(s), t, t === "image/jpeg" ? 0.92 : void 0);
    } catch {
      e(null);
    }
  });
}
function xe(i, t) {
  const e = Math.min(1, _e / Math.max(i, t)), s = Math.min(1, Math.sqrt(Ne / (i * t))), n = Math.min(e, s);
  return n >= 1 ? { width: i, height: t } : {
    width: Math.max(1, Math.round(i * n)),
    height: Math.max(1, Math.round(t * n))
  };
}
function wt(i, t, e, s, n) {
  const r = document.createElement("canvas");
  r.width = s, r.height = n;
  const a = r.getContext("2d");
  if (!a) throw new G("decode_failed", "无法取得画布上下文");
  return a.fillStyle = "#ffffff", a.fillRect(0, 0, s, n), a.drawImage(i, 0, 0, t, e, 0, 0, s, n), r;
}
async function Xt(i, t = {}) {
  if (i.size === 0) throw new G("empty", "图片不能为空文件");
  const e = new Uint8Array(await i.slice(0, 256).arrayBuffer()), s = $e(e);
  if (!s) throw new G("invalid_type", "仅支持 PNG / JPEG / WebP 静态图片");
  if (s === "image/webp" && Ve(e))
    throw new G("invalid_type", "不支持动图 WebP");
  const n = (i.type || "").toLowerCase();
  n && n !== s && n.startsWith("image/") && (i = new Blob([i], { type: s }));
  const r = await vt(i, t);
  try {
    const a = xe(r.width, r.height);
    if (a.width === r.width && a.height === r.height && i.size <= rt)
      return { blob: i, mime: s, width: r.width, height: r.height, oversize: !1 };
    let o = wt(r.source, r.width, r.height, a.width, a.height), h = s, d = await et(o, h);
    d || (h = "image/png", d = await et(o, h));
    let c = 0;
    for (; d && d.size > rt && c < me; ) {
      c++, o = wt(
        o,
        o.width,
        o.height,
        Math.max(1, Math.floor(o.width * 0.8)),
        Math.max(1, Math.floor(o.height * 0.8))
      );
      const f = await et(o, h);
      if (f) d = f;
      else break;
    }
    if (!d) throw new G("decode_failed", "图片重编码失败");
    return {
      blob: d,
      mime: h,
      width: o.width,
      height: o.height,
      oversize: d.size > rt
    };
  } finally {
    r.close?.();
  }
}
async function Dt(i) {
  if (!i || i.width <= 0 || i.height <= 0) return null;
  const t = xe(i.width, i.height);
  let e, s, n;
  if (t.width === i.width && t.height === i.height)
    e = await et(i, "image/png"), s = i.width, n = i.height;
  else {
    const a = wt(i, i.width, i.height, t.width, t.height);
    e = await et(a, "image/png"), s = a.width, n = a.height, i = a;
  }
  let r = 0;
  for (; e && e.size > rt && r < me; ) {
    r++, i = wt(
      i,
      i.width,
      i.height,
      Math.max(1, Math.floor(i.width * 0.8)),
      Math.max(1, Math.floor(i.height * 0.8))
    );
    const o = await et(i, "image/png");
    if (o)
      e = o, s = i.width, n = i.height;
    else break;
  }
  return e ? { blob: e, mime: "image/png", width: s, height: n, oversize: e.size > rt } : null;
}
function Gt(i) {
  if (!i) return !1;
  const t = i.trim();
  if (!t || t.startsWith("data:") || t.startsWith("blob:") || t.startsWith("#")) return !1;
  try {
    return new URL(t, document.baseURI).origin !== window.location.origin;
  } catch {
    return !1;
  }
}
function Ke(i) {
  try {
    const t = window.getComputedStyle(i);
    if (t.display === "none" || t.visibility !== "visible") return !1;
  } catch {
  }
  return i.getClientRects().length > 0;
}
function Xe(i, t) {
  let e = 0;
  const s = (n) => {
    const r = Array.from(n.querySelectorAll("iframe, img, image"));
    for (const a of r) {
      if (t?.(a) || !Ke(a)) continue;
      if (a.tagName.toUpperCase() === "IFRAME") {
        const h = a.getAttribute("src") ?? a.src;
        Gt(h) && e++;
      } else {
        const h = a, d = a.getAttribute("src") ?? a.getAttribute("href") ?? h.src;
        Gt(d) && a.getAttribute("crossorigin") === null && e++;
      }
    }
    for (const a of Array.from(n.querySelectorAll("*"))) {
      const o = a.shadowRoot;
      o && s(o);
    }
  };
  return s(i), e;
}
const Et = 2048, kt = 4e6, St = 5 * 1024 * 1024, Ge = 3, Ye = 0.8, Qe = { r: 110, g: 110, b: 115 }, Ht = "rgb(110, 110, 115)", ye = "截图未完成，可重试或继续文字反馈", ve = 'input[type="password"], [data-feedback-capture-mask]';
class w extends Error {
  reason;
  /** 面向用户的提示（不含内部细节）。 */
  userMessage;
  constructor(t, e, s = ye) {
    super(`[${t}] ${e}`), this.name = "CaptureError", this.reason = t, this.userMessage = s;
  }
}
function z(i) {
  return typeof i == "number" && Number.isFinite(i);
}
function Ze(i, t) {
  if (!i || typeof i != "object")
    throw new w("provider-invalid", "captureProvider 未返回结果对象");
  const e = i.blob;
  if (!(e instanceof Blob))
    throw new w("provider-invalid", "captureProvider 返回值缺少 Blob");
  if (!e.type.toLowerCase().includes("image/png"))
    throw new w("provider-invalid", "截图仅支持 PNG 格式");
  if (e.size === 0 || e.size > St)
    throw new w("provider-invalid", `截图文件大小需在 0–${St} 字节内`);
  if (!z(i.width) || !z(i.height))
    throw new w("provider-invalid", "截图尺寸坐标必须为有限数字");
  const s = Math.round(i.width), n = Math.round(i.height);
  if (s <= 0 || n <= 0)
    throw new w("provider-invalid", "截图尺寸必须为正数");
  if (Math.max(s, n) > Et)
    throw new w("provider-invalid", `截图输出边长不能超过 ${Et}px`);
  if (s * n > kt)
    throw new w("provider-invalid", `截图输出像素不能超过 ${kt}`);
  let r = [];
  if (t > 0) {
    if (i.sameFrameMasking !== !0 || !Array.isArray(i.maskedRegions))
      throw new w(
        "provider-unsafe",
        "页面存在可见敏感区域，但 captureProvider 未保证同帧遮挡，拒绝使用该截图"
      );
    for (const h of i.maskedRegions) {
      if (!h || !z(h.x) || !z(h.y) || !z(h.width) || !z(h.height))
        throw new w("provider-unsafe", "遮挡区域坐标非有限，拒绝使用该截图");
      if (h.width <= 0 || h.height <= 0)
        throw new w("provider-unsafe", "遮挡区域尺寸必须为正数");
      if (h.x < -1 || h.y < -1 || h.x + h.width > s + 1 || h.y + h.height > n + 1)
        throw new w("provider-unsafe", "遮挡区域超出截图输出范围");
    }
    if (i.maskedRegions.length === 0)
      throw new w(
        "provider-unsafe",
        `检测到 ${t} 个可见敏感区域，但提供者未遮挡任何区域`
      );
    r = i.maskedRegions.map((h) => ({
      x: h.x,
      y: h.y,
      width: h.width,
      height: h.height
    }));
  } else i.sameFrameMasking === !0 && Array.isArray(i.maskedRegions) && (r = i.maskedRegions.filter(
    (h) => h && z(h.x) && z(h.y) && z(h.width) && z(h.height) && h.width > 0 && h.height > 0
  ));
  let a = s, o = n;
  return i.viewport && z(i.viewport.width) && z(i.viewport.height) && i.viewport.width > 0 && i.viewport.height > 0 && (a = Math.round(i.viewport.width), o = Math.round(i.viewport.height)), { blob: e, viewportWidth: a, viewportHeight: o, outputWidth: s, outputHeight: n, maskedRegions: r };
}
function Nt(i, t = []) {
  t.push(...Array.from(i.querySelectorAll(ve)));
  const e = Array.from(i.querySelectorAll("*"));
  for (const s of e) {
    const n = s.shadowRoot;
    n && Nt(n, t);
  }
  return t;
}
function Je(i, t) {
  return t.getComputedStyle(i).visibility !== "visible" ? !1 : i.getClientRects().length > 0;
}
function $t(i, t = []) {
  typeof i.tagName == "string" && t.push(i);
  for (const e of Array.from(i.querySelectorAll("*"))) {
    t.push(e);
    const s = e.shadowRoot;
    s && $t(s, t);
  }
  return t;
}
function we(i, t, e, s) {
  const n = s.subtree ? $t(i) : [i];
  let r = 1 / 0, a = 1 / 0, o = -1 / 0, h = -1 / 0, d = !1;
  for (const A of n)
    if (Je(A, t))
      for (const v of Array.from(A.getClientRects())) {
        if (!Number.isFinite(v.left) || !Number.isFinite(v.top) || !Number.isFinite(v.right) || !Number.isFinite(v.bottom))
          throw new w("locate-failed", "敏感节点边界坐标非有限，无法安全遮挡");
        v.width <= 0 || v.height <= 0 || (d = !0, r = Math.min(r, v.left), a = Math.min(a, v.top), o = Math.max(o, v.right), h = Math.max(h, v.bottom));
      }
  if (!d) return null;
  const c = Math.max(0, Math.min(r, e.width)), u = Math.max(0, Math.min(a, e.height)), f = Math.max(0, Math.max(o, c)), x = Math.max(0, Math.max(h, u)), b = Math.min(f, e.width) - c, m = Math.min(x, e.height) - u;
  return b <= 0 || m <= 0 ? null : { x: c, y: u, width: b, height: m };
}
function ti(i, t = {}) {
  const e = i.defaultView;
  if (!e) return 0;
  const s = { width: e.innerWidth, height: e.innerHeight };
  let n = 0;
  for (const r of Nt(i)) {
    if (t.ignore?.(r)) continue;
    const a = r.hasAttribute("data-feedback-capture-mask");
    we(r, e, s, { subtree: !!a }) !== null && n++;
  }
  return n;
}
function ei(i) {
  const t = i.innerWidth, e = i.innerHeight;
  if (!z(t) || !z(e) || t <= 0 || e <= 0)
    throw new w("locate-failed", "无法获得有限的视口尺寸");
  const s = i.devicePixelRatio;
  return {
    width: t,
    height: e,
    scrollX: Number.isFinite(i.scrollX) ? i.scrollX : 0,
    scrollY: Number.isFinite(i.scrollY) ? i.scrollY : 0,
    dpr: z(s) && s > 0 ? s : 1
  };
}
function ii(i, t) {
  const e = Math.max(i.width, i.height), s = i.width * i.height;
  return Math.min(t, 2, Et / e, Math.sqrt(kt / s));
}
const si = `
${ve},
[data-feedback-capture-mask] *,
[data-feedback-capture-mask]::before,
[data-feedback-capture-mask]::after,
[data-feedback-capture-mask] *::before,
[data-feedback-capture-mask] *::after,
input[type="password"]::before,
input[type="password"]::after {
  visibility: hidden !important;
  background-image: none !important;
  box-shadow: none !important;
}
`;
function ni(i, t, e = 0) {
  const s = i.defaultView;
  if (!s) throw new w("locate-failed", "克隆文档缺少可布局的 window，无法定位敏感节点");
  const n = Nt(i), r = [];
  for (const o of n) {
    const h = we(o, s, t, { subtree: o.hasAttribute("data-feedback-capture-mask") });
    h && r.push(h);
  }
  if (r.length < e)
    throw new w(
      "locate-failed",
      `页面有 ${e} 个可见敏感区域，但克隆上仅定位到 ${r.length} 个，无法保证遮挡`
    );
  const a = i.createElement("style");
  a.setAttribute("data-feedback-capture-mask-style", ""), a.textContent = si, (i.head ?? i.documentElement).appendChild(a);
  for (const o of n) {
    const h = o.hasAttribute("data-feedback-capture-mask") ? $t(o) : [o];
    for (const d of h) {
      const c = d;
      c.style && (c.style.setProperty("visibility", "hidden", "important"), c.style.setProperty("background-image", "none", "important"));
      const u = d.tagName ? d.tagName.toUpperCase() : "";
      if ((u === "IMG" || u === "CANVAS" || u === "VIDEO" || u === "IFRAME") && (d.removeAttribute("src"), d.removeAttribute("srcset"), d.removeAttribute("poster")), u === "SOURCE" && (d.removeAttribute("src"), d.removeAttribute("srcset")), u === "IMAGE" && (d.removeAttribute("href"), d.removeAttribute("xlink:href")), u === "INPUT" && d.type === "password")
        try {
          d.value = "";
        } catch {
        }
    }
  }
  return r;
}
function ri(i, t, e, s, n) {
  if (!Number.isFinite(i.x) || !Number.isFinite(i.y) || !Number.isFinite(i.width) || !Number.isFinite(i.height))
    throw new w("locate-failed", "遮挡区域坐标非有限");
  if (!Number.isFinite(t) || !Number.isFinite(e) || t <= 0 || e <= 0)
    throw new w("verify-failed", "遮挡缩放比例非有限，无法映射到输出位图");
  const r = Math.max(0, Math.floor(i.x * t) - 1), a = Math.max(0, Math.floor(i.y * e) - 1), o = Math.min(s, Math.ceil((i.x + i.width) * t) + 1), h = Math.min(n, Math.ceil((i.y + i.height) * e) + 1), d = o - r, c = h - a;
  if (d <= 0 || c <= 0)
    throw new w("verify-failed", "遮挡区域投影到输出位图后退化，无法保证覆盖");
  return { x: r, y: a, width: d, height: c };
}
function oi(i, t) {
  const e = i.getContext("2d");
  if (!e) throw new w("verify-failed", "无法取得最终位图的 2D 上下文");
  e.save();
  try {
    e.setTransform(1, 0, 0, 1, 0, 0), e.globalAlpha = 1, e.globalCompositeOperation = "source-over", e.fillStyle = Ht;
    for (const s of t) e.fillRect(s.x, s.y, s.width, s.height);
  } finally {
    e.restore();
  }
}
function ai(i, t) {
  if (t.length === 0) return;
  const e = i.getContext("2d", { willReadFrequently: !0 });
  if (!e) throw new w("verify-failed", "无法读取最终位图验证遮挡结果");
  for (const s of t) {
    const n = Math.floor(s.x), r = Math.floor(s.y), a = Math.max(1, Math.ceil(s.width)), o = Math.max(1, Math.ceil(s.height));
    if (n < 0 || r < 0 || n + a > i.width || r + o > i.height)
      throw new w("verify-failed", "遮挡矩形超出画布范围，无法验证");
    let h;
    try {
      h = e.getImageData(n, r, a, o).data;
    } catch (m) {
      throw new w("verify-failed", `位图被污染或不可读，无法验证遮挡：${m.message}`);
    }
    const { r: d, g: c, b: u } = Qe, f = Math.max(1, Math.floor(a / 33)), x = Math.max(1, Math.floor(o / 33)), b = (m, A) => {
      const v = (A * a + m) * 4;
      return h[v] === d && h[v + 1] === c && h[v + 2] === u && h[v + 3] === 255;
    };
    for (let m = 0; m < o; m += x) {
      for (let A = 0; A < a; A += f)
        if (!b(A, m))
          throw new w("verify-failed", `遮挡验证失败：区域 (${n + A}, ${r + m}) 未被不透明覆盖`);
      if (!b(a - 1, m))
        throw new w("verify-failed", `遮挡验证失败：区域右缘 (${n + a - 1}, ${r + m}) 未被不透明覆盖`);
    }
    if (!b(0, o - 1) || !b(a - 1, o - 1))
      throw new w("verify-failed", "遮挡验证失败：区域底缘未被不透明覆盖");
  }
}
function Yt(i) {
  return new Promise((t, e) => {
    i.toBlob((s) => s ? t(s) : e(new w("verify-failed", "Canvas toBlob 失败")), "image/png");
  });
}
function li(i, t) {
  const e = Math.max(1, Math.floor(i.width * t)), s = Math.max(1, Math.floor(i.height * t)), n = document.createElement("canvas");
  n.width = e, n.height = s;
  const r = n.getContext("2d");
  if (!r) throw new w("verify-failed", "缩小重画无法取得 2D 上下文");
  return r.drawImage(i, 0, 0, e, s), n;
}
function hi(i, t) {
  if (!Number.isFinite(i) || !Number.isFinite(t) || i <= 0 || t <= 0)
    throw new w("verify-failed", "输出位图尺寸非有限");
  if (Math.max(i, t) > Et || i * t > kt)
    throw new w("verify-failed", "输出位图超出两端统一限制");
}
function It(i) {
  if (i?.aborted) throw new w("aborted", "截图会话已失效");
}
async function di() {
  const i = await import("./html2canvas-pro.esm-CQ8baKsv.js");
  return i.default || i;
}
async function ci(i) {
  It(i.signal);
  const { viewport: t } = i, e = ii(t, t.dpr);
  let s = [];
  const r = await (i.html2canvas ?? await di())(document.documentElement, {
    x: t.scrollX,
    y: t.scrollY,
    width: t.width,
    height: t.height,
    scale: e,
    useCORS: !0,
    allowTaint: !1,
    logging: !1,
    ignoreElements: (u) => i.ignore?.(u) ?? !1,
    onclone: (u) => {
      s = ni(u, t, i.expectedSensitiveCount);
    }
  });
  if (It(i.signal), !r || !Number.isFinite(r.width) || !Number.isFinite(r.height) || r.width <= 0 || r.height <= 0)
    throw new w("verify-failed", "html2canvas 未产出有效画布");
  let a = r, o = [];
  const h = () => {
    hi(a.width, a.height);
    const u = a.width / t.width, f = a.height / t.height;
    o = s.map(
      (x) => ri(x, u, f, a.width, a.height)
    ), o.length > 0 && (oi(a, o), ai(a, o));
  };
  h();
  let d = await Yt(a), c = 0;
  for (; d.size > St && c < Ge; )
    c++, It(i.signal), a = li(a, Ye), h(), d = await Yt(a);
  if (d.size > St)
    throw new w("size-limit", "缩小重编码三次后仍超过大小限制");
  return {
    blob: d,
    viewportWidth: t.width,
    viewportHeight: t.height,
    outputWidth: a.width,
    outputHeight: a.height,
    // 与最终输出位图一致（含缩小重编码后的坐标）的遮挡区域像素矩形
    maskedRegions: o.map((u) => ({ ...u }))
  };
}
const Qt = "#ff3b30", Zt = 3, tt = 4, Jt = 32, ui = 10, fi = 6, gi = 24;
function R(i, t, e) {
  const s = document.createElement(i);
  return t && (s.className = t), e !== void 0 && (s.textContent = e), s;
}
function pi(i) {
  const t = document.createElement("canvas");
  t.width = i.width, t.height = i.height;
  const e = t.getContext("2d");
  return e && e.drawImage(i, 0, 0), t;
}
function Z(i, t, e) {
  return Math.max(t, Math.min(i, e));
}
function te(i, t, e, s) {
  return {
    x: Math.min(i, e),
    y: Math.min(t, s),
    width: Math.abs(e - i),
    height: Math.abs(s - t)
  };
}
function ee(i, t, e) {
  const s = Math.max(0, Math.min(i.x, t)), n = Math.max(0, Math.min(i.y, e)), r = Math.max(0, Math.min(i.x + i.width, t)), a = Math.max(0, Math.min(i.y + i.height, e)), o = r - s, h = a - n;
  return o < tt || h < tt ? null : { x: s, y: n, width: o, height: h };
}
function bi(i, t, e, s, n) {
  const r = Z(i.x + t, 0, Math.max(0, s - i.width)), a = Z(i.y + e, 0, Math.max(0, n - i.height));
  return { x: r, y: a, width: i.width, height: i.height };
}
function mi(i, t, e, s, n, r) {
  let a = i.x, o = i.y, h = i.x + i.width, d = i.y + i.height;
  return t.includes("w") && (a = Z(a + e, 0, h - tt)), t.includes("e") && (h = Z(h + e, a + tt, n)), t.includes("n") && (o = Z(o + s, 0, d - tt)), t.includes("s") && (d = Z(d + s, o + tt, r)), { x: a, y: o, width: h - a, height: d - o };
}
function Mt(i) {
  const t = i.x, e = i.x + i.width / 2, s = i.x + i.width, n = i.y, r = i.y + i.height / 2, a = i.y + i.height;
  return [
    { handle: "nw", cx: t, cy: n },
    { handle: "n", cx: e, cy: n },
    { handle: "ne", cx: s, cy: n },
    { handle: "e", cx: s, cy: r },
    { handle: "se", cx: s, cy: a },
    { handle: "s", cx: e, cy: a },
    { handle: "sw", cx: t, cy: a },
    { handle: "w", cx: t, cy: r }
  ];
}
function xi(i, t) {
  return i.x >= t.x && i.x <= t.x + t.width && i.y >= t.y && i.y <= t.y + t.height;
}
function ie(i) {
  const { mount: t } = i, e = R("div", "fb-editor");
  e.setAttribute("role", "dialog"), e.setAttribute("aria-modal", "true"), e.setAttribute("aria-label", "编辑图片");
  const s = R("div", "fb-editor-toolbar"), n = R("div", "fb-editor-tools"), r = R("button", "fb-editor-tool", "裁剪"), a = R("button", "fb-editor-tool is-active", "矩形标注"), o = R("button", "fb-editor-tool", "遮挡"), h = R("button", "fb-editor-tool", "撤销");
  for (const g of [r, a, o, h])
    g.type = "button";
  r.setAttribute("aria-label", "裁剪：框选后调整选区并应用"), r.setAttribute("aria-pressed", "false"), a.setAttribute("aria-label", "矩形标注：拖拽绘制红色描边矩形"), a.setAttribute("aria-pressed", "true"), o.setAttribute("aria-label", "遮挡：拖拽绘制不透明遮盖块"), o.setAttribute("aria-pressed", "false"), h.setAttribute("aria-label", "撤销上一步编辑"), h.disabled = !0, n.append(r, a, o, h);
  const d = R("div", "fb-editor-actions"), c = R("span", "fb-editor-save-hint");
  c.hidden = !0;
  const u = R("button", "fb-editor-btn", "取消"), f = R("button", "fb-editor-btn is-primary", "保存");
  u.type = "button", f.type = "button", u.setAttribute("aria-label", "取消编辑并关闭"), f.setAttribute("aria-label", "保存编辑结果并替换当前图片"), d.append(c, u, f), s.append(n, d);
  const x = R("div", "fb-editor-main"), b = R("div", "fb-editor-stage"), m = R("canvas", "fb-editor-canvas");
  m.setAttribute("aria-label", "图片编辑画布"), b.append(m);
  const A = R("aside", "fb-editor-result");
  A.setAttribute("aria-label", "裁剪结果预览");
  const v = R("div", "fb-editor-result-title", "裁剪结果"), T = R("canvas", "fb-editor-preview");
  T.setAttribute("aria-label", "裁剪结果");
  const B = R("div", "fb-editor-result-size", "");
  A.append(v, T, B), x.append(b, A);
  const k = R("div", "fb-editor-cropbar");
  k.hidden = !0;
  const S = R("span", "fb-editor-crop-hint", "已框选裁剪区域：可拖动调整，或重新框选"), I = R("button", "fb-editor-btn is-primary", "应用裁剪"), M = R("button", "fb-editor-btn", "取消裁剪");
  I.type = "button", M.type = "button", I.setAttribute("aria-label", "应用当前裁剪选区"), M.setAttribute("aria-label", "取消当前裁剪选区"), k.append(S, I, M);
  const P = R(
    "p",
    "fb-editor-hint",
    "自动遮挡的敏感区域已写入图片，无法通过编辑恢复；辅助边框与控制点不会写入图片；保存将替换当前图片，不占用新的图片名额。"
  );
  e.append(s, x, k, P);
  const U = document.createElement("canvas");
  U.width = Math.max(1, Math.round(i.width)), U.height = Math.max(1, Math.round(i.height));
  {
    const g = U.getContext("2d");
    g && g.drawImage(i.image, 0, 0, U.width, U.height);
  }
  let q = "rect";
  const _ = [];
  let C = null, K = !1, D = null, j = null, N = null, W = !0, X = !1;
  function it() {
    let g = pi(U);
    for (const p of _) {
      const y = g.getContext("2d");
      if (y)
        if (p.type === "crop") {
          const L = document.createElement("canvas");
          L.width = Math.max(1, Math.round(p.rect.width)), L.height = Math.max(1, Math.round(p.rect.height));
          const F = L.getContext("2d");
          F && F.drawImage(
            g,
            Math.round(p.rect.x),
            Math.round(p.rect.y),
            Math.round(p.rect.width),
            Math.round(p.rect.height),
            0,
            0,
            L.width,
            L.height
          ), g = L;
        } else p.type === "rect" ? (y.save(), y.strokeStyle = Qt, y.lineWidth = Zt, y.strokeRect(p.rect.x, p.rect.y, p.rect.width, p.rect.height), y.restore()) : (y.save(), y.fillStyle = Ht, y.fillRect(p.rect.x, p.rect.y, p.rect.width, p.rect.height), y.restore());
    }
    return g;
  }
  function E(g) {
    const p = m.getBoundingClientRect(), y = p.width > 0 ? m.width / p.width : 1, L = Math.round(ui * y), F = Z(L, fi, gi);
    return Math.max(2, Math.min(F, Math.floor(Math.min(g.width, g.height) / 3) || 2));
  }
  function $(g, p) {
    const y = E(p);
    for (const L of Mt(p))
      if (Math.abs(g.x - L.cx) <= y / 2 + 2 && Math.abs(g.y - L.cy) <= y / 2 + 2) return L.handle;
    return null;
  }
  function Ae(g, p) {
    const y = m.width, L = m.height;
    g.save(), g.fillStyle = "rgba(0,0,0,0.45)", g.fillRect(0, 0, y, p.y), g.fillRect(0, p.y + p.height, y, L - p.y - p.height), g.fillRect(0, p.y, p.x, p.height), g.fillRect(p.x + p.width, p.y, y - p.x - p.width, p.height), g.strokeStyle = "#ffffff", g.lineWidth = 1, g.setLineDash([6, 4]), g.strokeRect(p.x + 0.5, p.y + 0.5, p.width, p.height), g.setLineDash([]);
    const F = E(p);
    g.fillStyle = "#ffffff";
    for (const st of Mt(p))
      g.fillRect(st.cx - F / 2, st.cy - F / 2, F, F);
    g.strokeStyle = "rgba(0,0,0,0.65)";
    for (const st of Mt(p))
      g.strokeRect(st.cx - F / 2, st.cy - F / 2, F, F);
    g.restore();
  }
  function Be(g) {
    const p = C, y = Math.max(1, Math.round(p ? p.width : g.width)), L = Math.max(1, Math.round(p ? p.height : g.height));
    T.width = y, T.height = L;
    const F = T.getContext("2d");
    F && (p ? F.drawImage(g, Math.round(p.x), Math.round(p.y), y, L, 0, 0, y, L) : F.drawImage(g, 0, 0)), B.textContent = `输出尺寸：${y}×${L} 像素`;
  }
  function ht() {
    const g = C !== null;
    f.disabled = X || g, c.hidden = !g, c.textContent = g ? "请先「应用裁剪」或「取消裁剪」后再保存" : "", g ? f.title = "存在未应用的裁剪选区" : f.removeAttribute("title");
  }
  function O() {
    if (!W) return;
    const g = it();
    m.width = g.width, m.height = g.height;
    const p = m.getContext("2d");
    p && p.drawImage(g, 0, 0);
    const y = K && D && j ? ee(te(D.x, D.y, j.x, j.y), m.width, m.height) : null;
    if (p)
      if (q === "crop") {
        const L = y ?? C;
        L && Ae(p, L);
      } else y && p && (q === "rect" ? (p.strokeStyle = Qt, p.lineWidth = Zt, p.strokeRect(y.x, y.y, y.width, y.height)) : (p.fillStyle = Ht, p.fillRect(y.x, y.y, y.width, y.height)));
    h.disabled = _.length === 0, k.hidden = C === null, Be(g), ht();
  }
  function dt(g) {
    q = g, r.classList.toggle("is-active", g === "crop"), a.classList.toggle("is-active", g === "rect"), o.classList.toggle("is-active", g === "mask"), r.setAttribute("aria-pressed", String(g === "crop")), a.setAttribute("aria-pressed", String(g === "rect")), o.setAttribute("aria-pressed", String(g === "mask")), g !== "crop" && C && (C = null, N = null, k.hidden = !0, O()), m.style.cursor = "crosshair";
  }
  function ct(g) {
    const p = m.getBoundingClientRect(), y = p.width > 0 ? m.width / p.width : 1, L = p.height > 0 ? m.height / p.height : 1;
    return {
      x: Math.round((g.clientX - p.left) * y),
      y: Math.round((g.clientY - p.top) * L)
    };
  }
  function Ct(g) {
    try {
      m.setPointerCapture(g.pointerId);
    } catch {
    }
    g.preventDefault();
  }
  m.addEventListener("pointerdown", (g) => {
    if (X || g.button !== 0) return;
    const p = ct(g);
    if (q === "crop" && C) {
      const y = $(p, C);
      if (y) {
        N = {
          mode: "resize",
          handle: y,
          startX: p.x,
          startY: p.y,
          startRect: { ...C }
        }, Ct(g);
        return;
      }
      if (xi(p, C)) {
        N = { mode: "move", startX: p.x, startY: p.y, startRect: { ...C } }, Ct(g);
        return;
      }
      C = null, k.hidden = !0, N = null, ht();
    }
    K = !0, D = p, j = p, Ct(g), O();
  }), m.addEventListener("pointermove", (g) => {
    if (N) {
      const p = ct(g), y = N;
      C = y.mode === "move" ? bi(y.startRect, p.x - y.startX, p.y - y.startY, m.width, m.height) : mi(
        y.startRect,
        y.handle ?? "se",
        p.x - y.startX,
        p.y - y.startY,
        m.width,
        m.height
      ), O();
      return;
    }
    !K || !D || (j = ct(g), O());
  });
  const Vt = (g, p) => {
    if (N) {
      N = null;
      try {
        m.releasePointerCapture(g.pointerId);
      } catch {
      }
      O();
      return;
    }
    if (!K || !D) return;
    K = !1;
    const y = p ? D : ct(g), L = ee(
      te(D.x, D.y, y.x, y.y),
      m.width,
      m.height
    );
    if (D = null, j = null, !L) {
      O();
      return;
    }
    if (q === "crop") {
      C = L, k.hidden = !1, O();
      return;
    }
    _.push({ type: q === "rect" ? "rect" : "mask", rect: L }), _.length > Jt && _.shift(), O();
  };
  m.addEventListener("pointerup", (g) => Vt(g, !1)), m.addEventListener("pointercancel", (g) => Vt(g, !0)), I.addEventListener("click", () => {
    C && (_.push({ type: "crop", rect: C }), C = null, k.hidden = !0, _.length > Jt && _.shift(), O());
  }), M.addEventListener("click", () => {
    C = null, k.hidden = !0, O();
  }), h.addEventListener("click", () => {
    if (C) {
      C = null, k.hidden = !0, O();
      return;
    }
    _.pop(), O();
  }), r.addEventListener("click", () => dt("crop")), a.addEventListener("click", () => dt("rect")), o.addEventListener("click", () => dt("mask"));
  const Rt = () => {
    W && (W = !1, document.removeEventListener("keydown", jt, !0), e.remove(), i.onCancel?.());
  }, Le = async () => {
    if (!(X || C !== null)) {
      X = !0, ht(), f.textContent = "保存中…";
      try {
        await i.onSave(it());
      } finally {
        X = !1, W && (f.textContent = "保存", ht());
      }
    }
  };
  f.addEventListener("click", () => {
    Le();
  }), u.addEventListener("click", Rt);
  const jt = (g) => {
    if (W) {
      if (g.key === "Escape") {
        g.preventDefault(), g.stopPropagation(), C ? (C = null, k.hidden = !0, O()) : Rt();
        return;
      }
      (g.metaKey || g.ctrlKey) && g.key.toLowerCase() === "z" && (g.preventDefault(), g.stopPropagation(), h.click());
    }
  };
  return document.addEventListener("keydown", jt, !0), t.appendChild(e), O(), dt("rect"), {
    close: Rt,
    get open() {
      return W;
    }
  };
}
const yi = 200, vi = 256 * 1024, wi = 48, Ei = 64, ki = 128;
function bt(i, t) {
  return i.length > t ? i.slice(0, t) : i;
}
function Si(i, t) {
  if (!i || typeof i.event != "string" || !i.event) return null;
  const e = { event: bt(i.event, wi) };
  e.at = typeof i.at == "string" && i.at ? i.at : (/* @__PURE__ */ new Date()).toISOString(), typeof i.code == "string" && i.code && (e.code = bt(i.code, Ei)), typeof i.status == "number" && Number.isFinite(i.status) && (e.status = Math.floor(i.status)), typeof i.durationMs == "number" && Number.isFinite(i.durationMs) && (e.durationMs = Math.max(0, Math.round(i.durationMs))), typeof i.method == "string" && i.method && (e.method = bt(i.method.toUpperCase(), 8));
  const s = i.pageLabel ?? t;
  return typeof s == "string" && s && (e.pageLabel = bt(s, ki)), e;
}
class ji {
  maxEntries;
  maxBytes;
  pageLabelOf;
  entries = [];
  bytes = 0;
  dropped = 0;
  constructor(t = {}) {
    this.maxEntries = Math.max(1, Math.floor(t.maxEntries ?? yi)), this.maxBytes = Math.max(1024, Math.floor(t.maxBytes ?? vi));
    const e = t.pageLabel;
    this.pageLabelOf = typeof e == "function" ? () => e() ?? null : () => typeof e == "string" ? e : null;
  }
  /** 记录一条白名单事件；非白名单字段（message/正文/URL/凭据）一律被丢弃。 */
  record(t) {
    const e = Si(t, this.pageLabelOf());
    if (!e) return;
    const n = JSON.stringify(e).length + 1;
    for (this.entries.push(e), this.bytes += n; this.entries.length > this.maxEntries || this.bytes > this.maxBytes; ) {
      const r = this.entries.shift();
      if (!r) break;
      this.bytes -= JSON.stringify(r).length + 1, this.dropped++;
    }
  }
  /** 便捷：记录 HTTP 请求结果（不记录 URL/正文/凭据）。 */
  recordFetch(t) {
    this.record({
      event: "fetch",
      method: t.method,
      status: t.status,
      durationMs: t.durationMs,
      code: t.code
    });
  }
  /** 当前缓冲条数（含被丢弃计数见 droppedCount）。 */
  get size() {
    return this.entries.length;
  }
  /** 因上限被丢弃的旧条目数（诊断时可见「曾丢弃」）。 */
  get droppedCount() {
    return this.dropped;
  }
  /** 新世代：清空缓冲（账号/服务切换时调用，旧写入绝不带入新身份）。 */
  clear() {
    this.entries = [], this.bytes = 0, this.dropped = 0;
  }
  /** 序列化为 JSONL 文本（UTF-8）。空缓冲返回空串。 */
  toJsonl() {
    if (this.entries.length === 0) return "";
    const t = this.entries.map((e) => JSON.stringify(e));
    return this.dropped > 0 && t.unshift(JSON.stringify({ event: "diagnostics_truncated", status: this.dropped })), t.join(`
`) + `
`;
  }
  /**
   * 产出与 `FeedbackLogProvider` 兼容的附件：空缓冲返回 null（不附加空文件），
   * 否则返回 `{ filename, blob }`（.jsonl、UTF-8、≤1MiB 由缓冲上限保证）。
   */
  toLogFile(t = "feedback-diagnostics.jsonl") {
    const e = this.toJsonl();
    return e ? { filename: t, blob: new Blob([e], { type: "application/jsonl" }) } : null;
  }
}
function Ai(i, t) {
  const e = t ?? ((s, n) => fetch(s, n));
  return async (s, n) => {
    const r = Date.now();
    let a = "GET";
    try {
      typeof s == "string" || s instanceof URL ? a = (n?.method ?? "GET").toUpperCase() : a = (s.method ?? n?.method ?? "GET").toUpperCase();
    } catch {
    }
    try {
      const o = await e(s, n);
      return i.recordFetch({ method: a, status: o.status, durationMs: Date.now() - r }), o;
    } catch (o) {
      throw i.recordFetch({
        method: a,
        durationMs: Date.now() - r,
        code: o instanceof Error ? o.name : "Error"
      }), o;
    }
  };
}
class Wi {
  recorder;
  target;
  installed = !1;
  onError;
  onRejection;
  constructor(t, e = {}) {
    this.recorder = t, this.target = e.target ?? (typeof window > "u" ? null : window), this.onError = (s) => {
      const n = s;
      this.recorder.record({
        event: "error",
        code: n.error instanceof Error ? n.error.name : "Error"
      });
    }, this.onRejection = (s) => {
      const r = s.reason;
      this.recorder.record({
        event: "unhandledrejection",
        code: r instanceof Error ? r.name : "Error"
      });
    };
  }
  /** 是否已挂载监听器。 */
  get active() {
    return this.installed;
  }
  /** 挂载监听（幂等）。宿主原有 window.onerror / 监听器完全不受影响。 */
  install() {
    this.installed || !this.target || (this.installed = !0, this.target.addEventListener("error", this.onError), this.target.addEventListener("unhandledrejection", this.onRejection));
  }
  /** 卸载监听（幂等）。 */
  uninstall() {
    !this.installed || !this.target || (this.installed = !1, this.target.removeEventListener("error", this.onError), this.target.removeEventListener("unhandledrejection", this.onRejection));
  }
}
function nt(i, t = {}) {
  const e = i.trim();
  if (!e) return { ok: !1, reason: "请输入服务器地址" };
  let s;
  try {
    s = new URL(e);
  } catch {
    return {
      ok: !1,
      reason: "地址格式无效（示例：https://fb.example.com 或 http://127.0.0.1:8787）"
    };
  }
  const n = s.protocol.toLowerCase();
  if (n !== "http:" && n !== "https:")
    return { ok: !1, reason: "仅支持 http(s) 地址（示例：https://fb.example.com）" };
  if (!s.hostname) return { ok: !1, reason: "地址缺少主机名" };
  if (s.username || s.password)
    return { ok: !1, reason: "地址不能包含用户名或密码" };
  if (s.search) return { ok: !1, reason: "地址不能包含查询参数" };
  if (s.hash) return { ok: !1, reason: "地址不能包含 # 片段" };
  if (t.pageIsHttps && n === "http:")
    return { ok: !1, reason: "HTTPS 页面无法使用 HTTP 服务地址（浏览器会拦截混合内容）" };
  const r = s.pathname.replace(/\/+$/, "");
  return { ok: !0, base: `${n}//${s.host}${r}` };
}
function Bi(i) {
  const t = new TextEncoder().encode(i);
  let e = "";
  for (const s of t) e += String.fromCharCode(s);
  return btoa(e).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}
function Ee(i, t) {
  const e = nt(t), n = `${e.ok ? e.base : t.trim()}\0${i}`;
  return `feedback-widget.server-override.${Bi(n)}`;
}
function Li(i, t, e) {
  try {
    const s = Ee(i, t);
    return e === null ? window.localStorage.removeItem(s) : window.localStorage.setItem(s, e), !0;
  } catch {
    return !1;
  }
}
function Ci(i) {
  return `web:${i}`.slice(0, 100);
}
function Ri(i) {
  if (i instanceof H)
    switch (i.code) {
      case "invalid_credentials":
        return "用户名或密码错误";
      case "rate_limited":
        return "尝试过于频繁，请稍后再试";
      case "origin_not_allowed":
        return "当前页面来源未被该应用允许登录，请联系管理员";
      case "unknown_app":
        return "应用未在服务端登记（请检查 app-id 配置）";
      default:
        return i.message;
    }
  return "网络异常，登录失败，请稍后重试";
}
const Ii = (
  /* css */
  `
:host {
  --fb-z-index-default: 2147483000;
  --fb-accent: #0071e3;
  --fb-accent-hover: #0077ed;
  --fb-accent-active: #006edb;
  --fb-accent-disabled: #a0c7f5;
  --fb-focus-ring: rgba(0, 113, 227, 0.28);

  /* 默认浅色变量 */
  --fb-bg: #ffffff;
  --fb-bg-subtle: #f5f5f7;
  --fb-bg-subtle-hover: #e8e8ed;
  --fb-text: #1d1d1f;
  --fb-text-secondary: #86868b;
  --fb-text-tertiary: #a1a1a6;
  --fb-border: rgba(0, 0, 0, 0.08);
  --fb-border-strong: rgba(0, 0, 0, 0.16);
  --fb-shadow: 0 12px 32px rgba(0, 0, 0, 0.12), 0 2px 6px rgba(0, 0, 0, 0.04);
  --fb-tab-shadow: 0 4px 14px rgba(0, 0, 0, 0.08), 0 1px 3px rgba(0, 0, 0, 0.05);

  --fb-status-error-bg: #fff2f4;
  --fb-status-error-border: #ffccd5;
  --fb-status-error-text: #d70015;

  --fb-status-success-bg: #f0fdf4;
  --fb-status-success-border: #bbf7d0;
  --fb-status-success-text: #15803d;

  --fb-status-warn-bg: #fff9eb;
  --fb-status-warn-border: #fde68a;
  --fb-status-warn-text: #b45309;

  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display",
    "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  font-size: 14px;
  line-height: 1.5;
  color: var(--fb-text);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/*
 * [hidden] 全局兜底（必须保留）。
 *
 * 浏览器默认样式表里的 [hidden] { display: none } 是 **UA 规则**，会被本文件的
 * 任何作者规则压掉 —— 例如 .fb-screenshot-wrap { display: flex } 会让
 * element.hidden = true 完全失效：元素照旧占据版面。
 *
 * 已发生过的真实故障（宿主未声明 capture-mode，即默认 off 的绝大多数宿主）：
 * 面板显示一个空的「当前截图 / 点击放大」区，缩略图 src 为空 → 破图占位，
 * 点击也无法放大（无截图时 openZoomModal 直接返回）。「移除截图」看起来也毫无反应。
 *
 * 因此这里统一恢复 hidden 语义，避免每加一个设置 display 的组件规则就重新踩一次。
 * 依赖它的元素不止截图层：面板、截图预览区与
 * 「截取当前页面 / 重新截图 / 移除截图」三件套（按状态互斥显示）同样靠它兜底。
 * 回归覆盖：e2e/run_browser.py 的 P5a / P5b / P6（只有真实浏览器有样式级联）。
 */
[hidden] {
  display: none !important;
}

/* 显式深色或系统深色模式 */
:host([theme="dark"]) {
  --fb-accent-disabled: #1d4677;
  --fb-bg: #1c1c1e;
  --fb-bg-subtle: #2c2c2e;
  --fb-bg-subtle-hover: #3a3a3c;
  --fb-text: #f5f5f7;
  --fb-text-secondary: #98989d;
  --fb-text-tertiary: #636366;
  --fb-border: rgba(255, 255, 255, 0.12);
  --fb-border-strong: rgba(255, 255, 255, 0.24);
  --fb-shadow: 0 16px 40px rgba(0, 0, 0, 0.45), 0 2px 6px rgba(0, 0, 0, 0.3);
  --fb-tab-shadow: 0 4px 14px rgba(0, 0, 0, 0.4);

  --fb-status-error-bg: rgba(255, 69, 58, 0.15);
  --fb-status-error-border: rgba(255, 69, 58, 0.3);
  --fb-status-error-text: #ff453a;

  --fb-status-success-bg: rgba(48, 209, 88, 0.15);
  --fb-status-success-border: rgba(48, 209, 88, 0.3);
  --fb-status-success-text: #30d158;

  --fb-status-warn-bg: rgba(255, 159, 10, 0.15);
  --fb-status-warn-border: rgba(255, 159, 10, 0.3);
  --fb-status-warn-text: #ff9f0a;
}

@media (prefers-color-scheme: dark) {
  :host(:not([theme="light"]):not([theme="dark"])) {
    --fb-accent-disabled: #1d4677;
    --fb-bg: #1c1c1e;
    --fb-bg-subtle: #2c2c2e;
    --fb-bg-subtle-hover: #3a3a3c;
    --fb-text: #f5f5f7;
    --fb-text-secondary: #98989d;
    --fb-text-tertiary: #636366;
    --fb-border: rgba(255, 255, 255, 0.12);
    --fb-border-strong: rgba(255, 255, 255, 0.24);
    --fb-shadow: 0 16px 40px rgba(0, 0, 0, 0.45), 0 2px 6px rgba(0, 0, 0, 0.3);
    --fb-tab-shadow: 0 4px 14px rgba(0, 0, 0, 0.4);

    --fb-status-error-bg: rgba(255, 69, 58, 0.15);
    --fb-status-error-border: rgba(255, 69, 58, 0.3);
    --fb-status-error-text: #ff453a;

    --fb-status-success-bg: rgba(48, 209, 88, 0.15);
    --fb-status-success-border: rgba(48, 209, 88, 0.3);
    --fb-status-success-text: #30d158;

    --fb-status-warn-bg: rgba(255, 159, 10, 0.15);
    --fb-status-warn-border: rgba(255, 159, 10, 0.3);
    --fb-status-warn-text: #ff9f0a;
  }
}

/* 隐藏宿主控制 */
:host([show-launcher="false"]) .fb-launcher {
  display: none !important;
}

/* ---- 侧边贴边入口标签 ---- */
.fb-launcher {
  position: fixed;
  z-index: var(--fb-z-index, var(--fb-z-index-default));
  bottom: clamp(16px, var(--fb-launcher-bottom, 25%), calc(100vh - 64px));
  right: 0;
  left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  min-width: 44px;
  padding: 8px 12px 8px 10px;
  border: 1px solid var(--fb-border);
  border-right: none;
  border-radius: 12px 0 0 12px;
  background: var(--fb-bg);
  color: var(--fb-text);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: var(--fb-tab-shadow);
  transition: transform 0.18s cubic-bezier(0.16, 1, 0.3, 1),
              background 0.18s ease,
              opacity 0.18s ease;
  user-select: none;
  -webkit-user-select: none;
}

.fb-launcher:hover {
  transform: translateX(-3px);
  background: var(--fb-bg-subtle);
}

.fb-launcher:active {
  transform: translateX(-1px);
}

.fb-launcher:focus-visible {
  outline: 2px solid var(--fb-accent);
  outline-offset: 1px;
}

.fb-launcher-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  fill: currentColor;
  color: var(--fb-accent);
}

:host([side="left"]) .fb-launcher {
  right: auto;
  left: 0;
  padding: 8px 10px 8px 12px;
  border-left: none;
  border-right: 1px solid var(--fb-border);
  border-radius: 0 12px 12px 0;
}

:host([side="left"]) .fb-launcher:hover {
  transform: translateX(3px);
}

:host([side="left"]) .fb-launcher:active {
  transform: translateX(1px);
}

/* ---- 灵感球 (Inspiration Orb) ----
   T1 视觉：静止 48px、拖动中展开到 72px；透明中心、白色高光边缘、
   青／紫／暖橙低透明度光晕。**渐变光效只允许出现在灵感球上**。 */
.fb-orb {
  position: fixed;
  z-index: var(--fb-z-index, var(--fb-z-index-default));
  bottom: clamp(16px, var(--fb-launcher-bottom, 25%), calc(100vh - 64px));
  right: 20px;
  left: auto;
  width: 48px;
  height: 48px;
  min-width: 48px;
  min-height: 48px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid rgba(125, 125, 130, 0.35);
  background: transparent;
  color: var(--fb-text);
  cursor: grab;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12), 0 1px 3px rgba(0, 0, 0, 0.06);
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
  transition: transform 0.18s cubic-bezier(0.16, 1, 0.3, 1),
              box-shadow 0.18s ease;
}

:host([side="left"]) .fb-orb {
  right: auto;
  left: 20px;
}

.fb-orb:hover:not(.is-dragging) {
  transform: scale(1.06);
}

.fb-orb:active:not(.is-dragging) {
  transform: scale(1.1);
}

/* 拖动中：尺寸展开由内联 transform scale(1.5)（48→72px）完成，此处关闭过渡 */
.fb-orb.is-dragging {
  cursor: grabbing;
  transition: none !important;
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.22), 0 2px 6px rgba(0, 0, 0, 0.1);
}

.fb-orb:focus-visible {
  outline: 2px solid var(--fb-accent);
  outline-offset: 2px;
}

/* 透明中心 + 白色高光边缘（内层） */
.fb-orb-core,
.fb-orb-glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.fb-orb-core {
  inset: 0;
  background: radial-gradient(
    circle at 50% 50%,
    rgba(255, 255, 255, 0) 0%,
    rgba(255, 255, 255, 0) 44%,
    rgba(255, 255, 255, 0.9) 60%,
    rgba(255, 255, 255, 0.35) 74%,
    rgba(255, 255, 255, 0) 84%
  );
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.55);
}

/* 青／紫／暖橙低透明度光晕（外层，缓慢流动） */
.fb-orb-glow {
  inset: -5px;
  background: conic-gradient(
    from 0deg,
    rgba(0, 200, 255, 0.38),
    rgba(160, 92, 255, 0.34),
    rgba(255, 158, 64, 0.32),
    rgba(0, 200, 255, 0.38)
  );
  filter: blur(7px);
  opacity: 0.7;
  animation: fb-orb-flow 7s linear infinite;
}

@keyframes fb-orb-flow {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

:host([theme="dark"]) .fb-orb {
  border: 1px solid rgba(255, 255, 255, 0.22);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.45);
}

@media (prefers-color-scheme: dark) {
  :host(:not([theme="light"]):not([theme="dark"])) .fb-orb {
    border: 1px solid rgba(255, 255, 255, 0.22);
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.45);
  }
}

/* 控制 launcher 模式显示/隐藏 */
:host(:not([launcher-mode="orb"])) .fb-orb {
  display: none !important;
}

:host([launcher-mode="orb"]) .fb-tab-launcher {
  display: none !important;
}

/* 面板打开时隐藏标签 */
.fb-launcher.is-hidden {
  display: none !important;
}

/* ---- 桌面圆角浮层面板（无遮罩、不挤压布局、不锁滚动） ----
   冻结规格：桌面宽 440px，最大高 min(720px, 视口高−32px)；768px 以下全屏。 */
.fb-panel {
  position: fixed;
  bottom: 16px;
  right: 16px;
  left: auto;
  top: auto;
  z-index: var(--fb-z-index, var(--fb-z-index-default));
  width: min(440px, calc(100vw - 32px));
  max-height: min(720px, calc(100dvh - 32px));
  height: auto;
  box-sizing: border-box;
  background: var(--fb-bg);
  border: 1px solid var(--fb-border);
  border-radius: 16px;
  box-shadow: var(--fb-shadow);
  display: flex;
  flex-direction: column;
  opacity: 0;
  transform: scale(0.96) translateY(8px);
  pointer-events: none;
  visibility: hidden;
  transition: opacity 0.2s cubic-bezier(0.16, 1, 0.3, 1),
              transform 0.2s cubic-bezier(0.16, 1, 0.3, 1),
              visibility 0.2s;
  overflow: hidden;
}

.fb-panel.is-open {
  opacity: 1;
  transform: scale(1) translateY(0);
  pointer-events: auto;
  visibility: visible;
}

:host([side="left"]) .fb-panel {
  right: auto;
  left: 16px;
}

/* ---- 移动端全屏布局（断点 768px） ---- */
@media (max-width: 767.98px) {
  .fb-panel {
    bottom: 0;
    top: 0;
    left: 0;
    right: 0;
    width: 100vw;
    max-height: 100dvh;
    height: 100dvh;
    border-radius: 0;
    border: none;
    box-shadow: none;
    transform: translateY(100%);
    padding-top: env(safe-area-inset-top, 0);
    padding-bottom: env(safe-area-inset-bottom, 0);
  }

  .fb-panel.is-open {
    transform: translateY(0);
  }
}

/* ---- 面板头部 ---- */
.fb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--fb-border);
  flex-shrink: 0;
}

.fb-title-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.fb-header-icon {
  width: 18px;
  height: 18px;
  fill: currentColor;
  color: var(--fb-accent);
  flex-shrink: 0;
}

.fb-header h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--fb-text);
  letter-spacing: -0.01em;
}

.fb-header-meta {
  font-size: 11px;
  color: var(--fb-text-tertiary);
  margin-left: 4px;
}

.fb-close {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: var(--fb-bg-subtle);
  color: var(--fb-text-secondary);
  border-radius: 50%;
  cursor: pointer;
  padding: 0;
  transition: background 0.15s, color 0.15s;
}

.fb-close:hover {
  background: var(--fb-bg-subtle-hover);
  color: var(--fb-text);
}

/* 命中区扩到 ≥44px（不改变视觉尺寸，相邻按钮间距按 16px 排布避免重叠） */
.fb-close::after,
.fb-icon-btn::after {
  content: "";
  position: absolute;
  inset: -6px;
}

.fb-close:focus-visible,
.fb-submit:focus-visible,
.fb-btn-secondary:focus-visible,
.fb-task-link:focus-visible,
.fb-retry-btn:focus-visible {
  outline: 2px solid var(--fb-accent);
  outline-offset: 2px;
}

/* ---- 面板主体 ----
   顺序（T2.6）：反馈描述 → 图片证据 → 可折叠日志 → 状态 → 固定提交区。 */
.fb-body {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  padding: 16px 18px 0;
  gap: 12px;
}

.fb-prompt {
  font-size: 13px;
  font-weight: 500;
  color: var(--fb-text-secondary);
  margin: 0;
}

.fb-textarea-wrap {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  min-height: 150px;
}

.fb-textarea {
  width: 100%;
  box-sizing: border-box;
  flex: 1;
  min-height: 150px;
  resize: none;
  padding: 12px 14px;
  border: 1px solid var(--fb-border);
  border-radius: 10px;
  background: var(--fb-bg-subtle);
  font: inherit;
  font-size: 14px;
  color: var(--fb-text);
  line-height: 1.5;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}

.fb-textarea:focus {
  outline: none;
  background: var(--fb-bg);
  border-color: var(--fb-accent);
  box-shadow: 0 0 0 3px var(--fb-focus-ring);
}

.fb-textarea:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.fb-counter-row {
  display: flex;
  justify-content: flex-end;
}

.fb-counter {
  color: var(--fb-text-tertiary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.fb-counter.is-over {
  color: var(--fb-status-error-text);
  font-weight: 600;
}

/* ---- 状态区与卡片 ---- */
.fb-status-region {
  min-height: 1.4em;
  font-size: 13px;
  color: var(--fb-text-secondary);
}

.fb-status-card {
  border-radius: 10px;
  padding: 12px 14px;
  border: 1px solid;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
}

.fb-status-card p {
  margin: 0;
  line-height: 1.45;
}

.fb-status-card.is-error {
  background: var(--fb-status-error-bg);
  border-color: var(--fb-status-error-border);
  color: var(--fb-status-error-text);
}

.fb-status-card.is-success {
  background: var(--fb-status-success-bg);
  border-color: var(--fb-status-success-border);
  color: var(--fb-status-success-text);
}

.fb-status-card.is-warn {
  background: var(--fb-status-warn-bg);
  border-color: var(--fb-status-warn-border);
  color: var(--fb-status-warn-text);
}

.fb-card-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
}

.fb-task-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--fb-accent);
  font-weight: 500;
  text-decoration: none;
}

.fb-task-link:hover {
  text-decoration: underline;
}

.fb-btn-secondary {
  border: 1px solid var(--fb-border-strong);
  background: var(--fb-bg);
  color: var(--fb-text);
  border-radius: 8px;
  min-height: 44px;
  padding: 8px 14px;
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}

.fb-btn-secondary:hover {
  background: var(--fb-bg-subtle-hover);
}

/* ---- 底部操作区（固定提交区）：滚动时始终贴在面板底部 ---- */
.fb-footer {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: auto;
  position: sticky;
  bottom: 0;
  z-index: 1;
  background: var(--fb-bg);
  border-top: 1px solid var(--fb-border);
  padding: 10px 0 16px;
}

.fb-submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 44px;
  padding: 0 18px;
  border: none;
  border-radius: 980px;
  background: var(--fb-accent);
  color: #fff;
  font: inherit;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s ease, transform 0.1s ease;
}

.fb-submit:hover:not(:disabled) {
  background: var(--fb-accent-hover);
}

.fb-submit:active:not(:disabled) {
  background: var(--fb-accent-active);
  transform: scale(0.99);
}

.fb-submit:disabled {
  background: var(--fb-accent-disabled);
  cursor: not-allowed;
}

.fb-footnote {
  margin: 0;
  font-size: 11px;
  color: var(--fb-text-tertiary);
  text-align: center;
  line-height: 1.4;
}

/* ---- 额度与面板内登录 ----
   技术 / 账户信息弱化展示（T2.6）：更小字号、三级文字色，不与主内容争焦点。 */
.fb-quota {
  font-size: 11px;
  color: var(--fb-text-tertiary);
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.fb-quota-blocked {
  margin: 0;
  font-size: 12px;
  color: var(--fb-status-warn-text);
  text-align: center;
  line-height: 1.4;
}

.fb-login-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--fb-border);
  border-radius: 10px;
  background: var(--fb-bg-subtle);
}

.fb-login-row {
  display: flex;
  gap: 8px;
}

.fb-login-input {
  flex: 1;
  min-width: 0;
  box-sizing: border-box;
  padding: 8px 10px;
  border: 1px solid var(--fb-border-strong);
  border-radius: 8px;
  background: var(--fb-bg);
  color: var(--fb-text);
  font: inherit;
  font-size: 13px;
}

.fb-login-input:focus {
  outline: none;
  border-color: var(--fb-accent);
  box-shadow: 0 0 0 3px var(--fb-focus-ring);
}

.fb-login-input:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.fb-login-error {
  margin: 0;
  font-size: 12px;
  color: var(--fb-status-error-text);
  line-height: 1.4;
}

.fb-login-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

/* ---- 截图区：预览与操作分离 ----
   预览（.fb-screenshot-wrap）只在真的有可渲染的截图 URL 时可见；
   操作区（.fb-screenshot-actions）始终在面板里：没有截图时它是「截取当前页面」。
   这条分工是刻意的——**无截图时整个截图区一起隐藏**会让默认 capture-mode=off
   （不自动截图）的宿主没有任何首次截图 / 补拍入口，而 hidden 的按钮三件套
   （截取当前页面 / 重新截图 / 移除截图）又必须真的不占版面。 */
.fb-shot-area {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* ---- 截图缩略图与操作（v12：两列 120px 缩略图网格；T2.5） ----
   图片完整显示（contain，禁止 cover 二次裁切）；角标标「局部截图 / 已编辑」，
   尺寸读数显示输出像素；编辑、删除按钮在图片**下方**，不得遮住图片内容。 */
.fb-screenshot-wrap {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.fb-screenshot-thumb-box {
  position: relative;
  width: 100%;
  border-radius: 10px;
  border: 1px solid var(--fb-border);
  background: var(--fb-bg-subtle);
  overflow: hidden;
  cursor: zoom-in;
  display: flex;
  flex-direction: column;
}

.fb-thumb-frame {
  position: relative;
  height: 116px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--fb-bg-subtle);
}

/* 编辑 / 删除行（图片下方）：触控目标 ≥44px，绝不覆盖图片内容。 */
.fb-thumb-actions {
  display: flex;
  gap: 8px;
  padding: 6px 8px;
  border-top: 1px solid var(--fb-border);
  background: var(--fb-bg);
}

.fb-thumb-edit,
.fb-thumb-remove {
  flex: 1;
  min-width: 44px;
  min-height: 44px;
  padding: 0 6px;
  font-size: 12px;
  line-height: 1;
  border: 1px solid var(--fb-border-strong);
  border-radius: 8px;
  background: var(--fb-bg);
  color: var(--fb-text);
  cursor: pointer;
}

.fb-thumb-remove {
  color: var(--fb-status-error-text);
  border-color: var(--fb-status-error-border);
}

.fb-thumb-edit:disabled,
.fb-thumb-remove:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.fb-thumb-edit:focus-visible,
.fb-thumb-remove:focus-visible {
  outline: 2px solid var(--fb-accent);
  outline-offset: 1px;
}

.fb-screenshot-thumb {
  width: 100%;
  height: 100%;
  object-fit: contain; /* 完整显示，禁止 cover 二次裁切 */
  display: block;
}

.fb-screenshot-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.65);
  color: #fff;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.02em;
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  pointer-events: none;
}

/* 尺寸读数（输出像素）：与角标同层的轻量标记 */
.fb-screenshot-size {
  position: absolute;
  bottom: 8px;
  left: 8px;
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.65);
  color: #fff;
  font-size: 10px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
  pointer-events: none;
}

.fb-screenshot-zoom-hint {
  position: absolute;
  bottom: 8px;
  right: 8px;
  padding: 3px 8px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.65);
  color: #fff;
  font-size: 11px;
  font-weight: 500;
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.fb-screenshot-thumb-box:hover .fb-screenshot-zoom-hint {
  opacity: 1;
}

.fb-screenshot-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

/* 首个截图入口：默认 capture-mode=off 的宿主里它是**唯一**的截图方式，
   因此比「重新截图 / 移除截图」更显眼（主色填充），但不与「提交」争主次。 */
.fb-btn-capture {
  min-height: 44px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
  border-radius: 8px;
  border: 1px solid var(--fb-accent);
  background: var(--fb-accent);
  color: #fff;
  cursor: pointer;
  transition: background 0.15s;
}

.fb-btn-capture:hover:not(:disabled) {
  background: var(--fb-accent-hover);
}

.fb-btn-capture:disabled {
  background: var(--fb-accent-disabled);
  border-color: var(--fb-accent-disabled);
  cursor: not-allowed;
}

/* 捕获中：面板在此期间整体 visibility:hidden（截图里不含组件自身 UI），
   所以这个加载态只可能被会话结束后的用户与辅助技术看到。 */
.fb-shot-area.is-capturing .fb-btn-capture {
  cursor: progress;
}

.fb-btn-retake,
.fb-btn-remove,
.fb-btn-add-image {
  min-height: 44px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
  border-radius: 8px;
  border: 1px solid var(--fb-border-strong);
  background: var(--fb-bg);
  color: var(--fb-text);
  cursor: pointer;
  transition: background 0.15s;
}

.fb-btn-retake:hover:not(:disabled),
.fb-btn-remove:hover:not(:disabled),
.fb-btn-add-image:hover:not(:disabled) {
  background: var(--fb-bg-subtle-hover);
}

.fb-btn-retake:disabled,
.fb-btn-remove:disabled,
.fb-btn-add-image:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 预览旁提示与图片错误（v12：部分内容未入图 / 服务端单图降级 / 仍超限） */
.fb-shot-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--fb-status-warn-text);
  background: var(--fb-status-warn-bg);
  border: 1px solid var(--fb-status-warn-border);
  border-radius: 8px;
  padding: 6px 10px;
}

.fb-shot-error {
  margin: 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--fb-status-error-text);
  background: var(--fb-status-error-bg);
  border: 1px solid var(--fb-status-error-border);
  border-radius: 8px;
  padding: 6px 10px;
}

/* 拖入图片时的目标态 */
.fb-body.is-dragover {
  outline: 2px dashed var(--fb-accent);
  outline-offset: -4px;
  border-radius: 8px;
}

.fb-btn-remove {
  color: var(--fb-status-error-text);
  border-color: var(--fb-status-error-border);
}

.fb-btn-capture:focus-visible,
.fb-btn-retake:focus-visible,
.fb-btn-remove:focus-visible,
.fb-zoom-close:focus-visible {
  outline: 2px solid var(--fb-accent);
  outline-offset: 2px;
}

/* 窄屏（移动端全屏面板）：截图操作按钮给到触屏可点的高度，并允许换行 */
@media (max-width: 767.98px) {
  .fb-screenshot-actions {
    gap: 10px;
  }

  .fb-btn-capture,
  .fb-btn-retake,
  .fb-btn-remove {
    padding: 7px 12px;
    font-size: 13px;
  }
}

/* ---- 日志附件区（T4 可折叠）：默认只显示简短摘要行 ---- */
.fb-logs-area {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.fb-logs-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-height: 44px;
  padding: 6px 12px;
  border: 1px solid var(--fb-border);
  border-radius: 10px;
  background: var(--fb-bg-subtle);
  color: var(--fb-text-secondary);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.fb-logs-toggle:hover {
  background: var(--fb-bg-subtle-hover);
}

.fb-logs-toggle:focus-visible {
  outline: 2px solid var(--fb-accent);
  outline-offset: 2px;
}

.fb-logs-summary {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fb-logs-chevron {
  flex-shrink: 0;
  transition: transform 0.18s ease;
}

.fb-logs-toggle[aria-expanded="true"] .fb-logs-chevron {
  transform: rotate(90deg);
}

.fb-logs-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.fb-logs-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--fb-text-secondary);
}

.fb-logs-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.fb-log-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  border-radius: 6px;
  background: var(--fb-bg-subtle);
  border: 1px solid var(--fb-border);
  font-size: 12px;
  gap: 8px;
}

.fb-log-info {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.fb-log-name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fb-log-badge {
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 500;
  background: var(--fb-bg-subtle-hover);
  color: var(--fb-text-secondary);
}

.fb-log-size {
  color: var(--fb-text-tertiary);
  font-size: 11px;
}

.fb-log-btns {
  display: flex;
  align-items: center;
  gap: 6px;
}

.fb-log-btn-preview,
.fb-log-btn-remove,
.fb-btn-add-log {
  min-height: 44px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 8px;
  border: 1px solid var(--fb-border-strong);
  background: var(--fb-bg);
  color: var(--fb-text);
  cursor: pointer;
  transition: background 0.15s;
}

.fb-log-btn-preview:hover,
.fb-log-btn-remove:hover,
.fb-btn-add-log:hover {
  background: var(--fb-bg-subtle-hover);
}

.fb-log-btn-remove {
  color: var(--fb-status-error-text);
  border-color: var(--fb-status-error-border);
}

.fb-log-status {
  font-size: 12px;
  color: var(--fb-text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.fb-log-error {
  font-size: 12px;
  color: var(--fb-status-error-text);
  background: var(--fb-status-error-bg);
  border: 1px solid var(--fb-status-error-border);
  padding: 4px 8px;
  border-radius: 4px;
}

.fb-log-preview-modal {
  position: fixed;
  inset: 0;
  z-index: calc(var(--fb-z-index, var(--fb-z-index-default)) + 25);
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  box-sizing: border-box;
  opacity: 0;
  pointer-events: none;
  visibility: hidden;
  transition: opacity 0.2s cubic-bezier(0.16, 1, 0.3, 1), visibility 0.2s;
}

.fb-log-preview-modal.is-open {
  opacity: 1;
  pointer-events: auto;
  visibility: visible;
}

.fb-log-preview-card {
  background: var(--fb-bg);
  color: var(--fb-text);
  border-radius: 12px;
  padding: 16px;
  max-width: 680px;
  width: 100%;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--fb-shadow);
  border: 1px solid var(--fb-border);
}

.fb-log-preview-body {
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.4;
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--fb-bg-subtle);
  padding: 12px;
  border-radius: 6px;
  margin: 12px 0;
  flex: 1;
}

/* ---- 截图大图弹窗 (Zoom Modal) ---- */
.fb-zoom-modal {
  position: fixed;
  inset: 0;
  z-index: calc(var(--fb-z-index, var(--fb-z-index-default)) + 20);
  background: rgba(0, 0, 0, 0.82);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  box-sizing: border-box;
  cursor: zoom-out;
  opacity: 0;
  pointer-events: none;
  visibility: hidden;
  transition: opacity 0.2s cubic-bezier(0.16, 1, 0.3, 1), visibility 0.2s;
}

.fb-zoom-modal.is-open {
  opacity: 1;
  pointer-events: auto;
  visibility: visible;
}

.fb-zoom-modal img {
  max-width: 92vw;
  max-height: 90vh;
  object-fit: contain;
  border-radius: 8px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.6);
}

.fb-zoom-close {
  position: absolute;
  top: 20px;
  right: 20px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}

.fb-zoom-close:hover {
  background: rgba(255, 255, 255, 0.3);
}

/* ---- 头部右侧动作组与服务器设置视图（T6） ---- */
.fb-header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.fb-icon-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--fb-text-secondary);
  border-radius: 50%;
  cursor: pointer;
  padding: 0;
  transition: background 0.15s, color 0.15s;
}

.fb-icon-btn:hover {
  background: var(--fb-bg-subtle-hover);
  color: var(--fb-text);
}

.fb-icon-btn:focus-visible {
  outline: 2px solid var(--fb-accent);
  outline-offset: 2px;
}

.fb-settings {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  padding: 16px 18px 18px;
  gap: 10px;
}

.fb-settings-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--fb-text);
}

.fb-settings-line {
  margin: 0;
  font-size: 12px;
  color: var(--fb-text-secondary);
  display: flex;
  gap: 4px;
  min-width: 0;
}

.fb-settings-label {
  flex-shrink: 0;
}

.fb-settings-value {
  color: var(--fb-text);
  overflow-wrap: anywhere;
  word-break: break-all;
}

.fb-server-input {
  width: 100%;
}

.fb-settings-error {
  margin: 0;
  font-size: 12px;
  color: var(--fb-status-error-text);
  line-height: 1.4;
}

.fb-settings-hint {
  margin: 0;
  font-size: 12px;
  color: var(--fb-status-warn-text);
  line-height: 1.4;
}

.fb-settings-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.fb-settings-confirm {
  border: 1px solid var(--fb-status-warn-border);
  background: var(--fb-status-warn-bg);
  color: var(--fb-status-warn-text);
  border-radius: 10px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
}

.fb-settings-confirm-text {
  margin: 0;
  line-height: 1.5;
}

/* ---- v11 对话与历史视图 ---- */
.fb-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #d70015;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}

.fb-launcher-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.2);
}

.fb-nav-tabs {
  display: flex;
  gap: 8px;
  align-items: center;
}

.fb-nav-btn {
  background: transparent;
  border: none;
  font-size: 14px;
  font-weight: 500;
  color: var(--fb-text-secondary);
  cursor: pointer;
  min-height: 44px;
  padding: 4px 12px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.fb-nav-btn:hover {
  background: var(--fb-bg-subtle-hover);
  color: var(--fb-text);
}

.fb-nav-btn.active {
  color: var(--fb-accent);
  font-weight: 600;
  background: var(--fb-bg-subtle);
}

.fb-history-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow-y: auto;
  gap: 8px;
  padding: 12px 0;
}

.fb-history-item {
  display: flex;
  flex-direction: column;
  padding: 10px 12px;
  background: var(--fb-bg-subtle);
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid transparent;
  text-decoration: none;
  color: inherit;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.fb-history-item:hover {
  background: var(--fb-bg-subtle-hover);
  border-color: var(--fb-border);
}

.fb-history-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.fb-history-title {
  font-weight: 600;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.fb-history-tags {
  display: flex;
  gap: 4px;
  align-items: center;
}

.fb-tag {
  display: inline-flex;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  background: var(--fb-border);
  color: var(--fb-text-secondary);
}

.fb-tag.waiting-admin {
  background: var(--fb-status-warn-bg);
  color: var(--fb-status-warn-text);
}

.fb-tag.waiting-user {
  background: var(--fb-status-success-bg);
  color: var(--fb-status-success-text);
}

.fb-tag.resolved {
  background: var(--fb-bg-subtle-hover);
  color: var(--fb-text-secondary);
}

.fb-dialogue-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.fb-dialogue-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0 10px;
  border-bottom: 1px solid var(--fb-border);
}

.fb-dialogue-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.fb-dialogue-msg {
  display: flex;
  flex-direction: column;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.4;
}

.fb-dialogue-msg.user {
  background: var(--fb-bg-subtle);
  border-left: 3px solid var(--fb-accent);
}

.fb-dialogue-msg.admin {
  background: #eef2ff;
  border-left: 3px solid #6366f1;
}

.fb-dialogue-msg.system {
  background: var(--fb-status-warn-bg);
  border-left: 3px solid var(--fb-status-warn-text);
}

.fb-dialogue-msg-meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--fb-text-secondary);
  margin-bottom: 4px;
}

.fb-dialogue-reply-box {
  border-top: 1px solid var(--fb-border);
  padding-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 窄屏（移动端全屏面板）：设置操作按钮给到触屏可点的高度 */
@media (max-width: 767.98px) {
  .fb-settings-actions .fb-btn-secondary {
    padding: 8px 14px;
    font-size: 13px;
  }
}

/* ---- 尊重 prefers-reduced-motion ----
   灵感球：取消光晕流动与缩放（拖动平移是功能反馈，保留）。 */
@media (prefers-reduced-motion: reduce) {
  .fb-launcher,
  .fb-panel,
  .fb-submit,
  .fb-close {
    transition: none !important;
  }
  .fb-tab-launcher:hover,
  .fb-tab-launcher:active,
  .fb-submit:active {
    transform: none !important;
  }
  .fb-orb,
  .fb-orb:hover:not(.is-dragging),
  .fb-orb:active:not(.is-dragging) {
    transition: none !important;
  }
  .fb-orb:hover:not(.is-dragging),
  .fb-orb:active:not(.is-dragging) {
    transform: none !important;
  }
  .fb-orb-glow {
    animation: none !important;
  }
}

/* ---- v12 全屏图片编辑器 ----
   覆盖在面板与放大预览之上；深色工作台以保证任何截图内容都有可比性。 */
.fb-editor {
  position: fixed;
  inset: 0;
  z-index: 2147483200;
  display: flex;
  flex-direction: column;
  background: #1c1c1e;
  color: #f5f5f7;
}

.fb-editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
  flex-wrap: wrap;
}

.fb-editor-tools {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.fb-editor-tool {
  min-height: 44px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.24);
  background: transparent;
  color: #f5f5f7;
  cursor: pointer;
  transition: background 0.15s;
}

.fb-editor-tool:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.12);
}

.fb-editor-tool.is-active {
  background: var(--fb-accent);
  border-color: var(--fb-accent);
  color: #fff;
}

.fb-editor-tool:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.fb-editor-tool:focus-visible,
.fb-editor-btn:focus-visible {
  outline: 2px solid var(--fb-focus-ring);
  outline-offset: 2px;
}

.fb-editor-actions {
  display: flex;
  gap: 8px;
}

.fb-editor-btn {
  min-height: 44px;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 600;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.24);
  background: transparent;
  color: #f5f5f7;
  cursor: pointer;
}

.fb-editor-btn.is-primary {
  background: var(--fb-accent);
  border-color: var(--fb-accent);
  color: #fff;
}

.fb-editor-btn.is-primary:hover:not(:disabled) {
  background: var(--fb-accent-hover);
}

.fb-editor-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.fb-editor-main {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px 16px;
  min-height: 0;
  container-type: inline-size;
}

.fb-editor-stage {
  flex: 1 1 320px;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: auto;
}

/* T2：独立的「裁剪结果」预览 + 输出尺寸（桌面右侧 280px） */
.fb-editor-result {
  flex: 0 0 280px;
  min-width: 220px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 12px;
  background: #2c2c2e;
  overflow: hidden;
}

.fb-editor-result-title {
  font-size: 12px;
  color: #98989d;
}

.fb-editor-preview {
  flex: 1 1 auto;
  min-height: 0;
  max-width: 100%;
  max-height: 100%;
  align-self: center;
  background:
    repeating-conic-gradient(#3a3a3c 0% 25%, #2c2c2e 0% 50%) 0 / 16px 16px;
  border-radius: 8px;
}

.fb-editor-result-size {
  font-size: 12px;
  color: #c7c7cc;
  font-variant-numeric: tabular-nums;
}

/* 窄屏（768px 以下）或容器过窄：上下排列，结果区高 160px */
@container (max-width: 767px) {
  .fb-editor-stage {
    flex: 1 1 100%;
  }
  .fb-editor-result {
    flex: 1 1 100%;
    min-width: 0;
    height: 160px;
  }
}

@media (max-width: 767.98px) {
  .fb-editor-stage {
    flex: 1 1 100%;
  }
  .fb-editor-result {
    flex: 1 1 100%;
    min-width: 0;
    height: 160px;
  }
}

.fb-editor-canvas {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  background:
    repeating-conic-gradient(#3a3a3c 0% 25%, #2c2c2e 0% 50%) 0 / 16px 16px;
  border-radius: 4px;
  cursor: crosshair;
  touch-action: none; /* 指针绘制不被手势滚动抢占 */
}

.fb-editor-cropbar {
  position: sticky;
  bottom: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 10px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.12);
  background: #1c1c1e;
}

/* 存在未应用选区时禁用「保存」的原因说明 */
.fb-editor-save-hint {
  font-size: 12px;
  color: #ff9f0a;
  line-height: 1.3;
  max-width: 240px;
}

.fb-editor-crop-hint {
  font-size: 12px;
  color: #98989d;
}

.fb-editor-hint {
  margin: 0;
  padding: 8px 16px 12px;
  font-size: 11px;
  color: #98989d;
  text-align: center;
}

/* ---- v12 我的反馈：筛选与分页 ---- */
.fb-history-filter-row {
  display: flex;
  padding: 8px 0;
}

.fb-history-filter {
  flex: 1;
  min-height: 44px;
  padding: 6px 10px;
  font-size: 13px;
  font-family: inherit;
  color: var(--fb-text);
  background: var(--fb-bg);
  border: 1px solid var(--fb-border-strong);
  border-radius: 8px;
}

.fb-history-filter:focus-visible {
  outline: 2px solid var(--fb-focus-ring);
  outline-offset: 1px;
}

.fb-history-items {
  flex: 1;
  overflow-y: auto;
}

.fb-history-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 8px 0;
}

.fb-history-pager .fb-btn-secondary {
  min-height: 44px;
  padding: 6px 14px;
  font-size: 12px;
}

.fb-history-page-info {
  font-size: 12px;
  color: var(--fb-text-secondary);
}

/* ---- v12 处理说明 ---- */
.fb-resolution-note {
  margin: 6px 0 0;
  padding: 6px 10px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--fb-status-success-text);
  background: var(--fb-status-success-bg);
  border: 1px solid var(--fb-status-success-border);
  border-radius: 8px;
  white-space: pre-wrap;
}

/* ---- v12 对话附件与回复图片 ---- */
.fb-dialogue-attachment-img {
  max-height: 100px;
  border-radius: 4px;
  border: 1px solid var(--fb-border);
}

.fb-reply-images {
  margin-top: 2px;
}

.fb-reply-thumb {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.fb-reply-thumb-img {
  width: 48px;
  height: 48px;
  object-fit: contain;
  border-radius: 6px;
  border: 1px solid var(--fb-border);
  cursor: zoom-in;
}
/* ---- T1 局部选区覆盖层（冻结画面 + 可调整选区 + 三个操作） ---- */
.fb-region-overlay {
  position: fixed;
  inset: 0;
  z-index: calc(var(--fb-z-index, var(--fb-z-index-default)) + 30);
  overflow: hidden;
  background: #000;
  cursor: crosshair;
}

.fb-region-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: fill; /* 冻结位图铺满覆盖层：与 0..1 归一化坐标一一对应 */
  pointer-events: none;
  user-select: none;
  -webkit-user-drag: none;
}

.fb-region-frame {
  position: absolute;
  box-sizing: border-box;
  border: 1px solid rgba(255, 255, 255, 0.95);
  /* 外部暗化遮罩（覆盖层 overflow:hidden 限制在视口内） */
  box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.45);
  cursor: move;
}

.fb-region-frame:focus-visible {
  outline: 2px solid var(--fb-accent);
  outline-offset: 2px;
}

.fb-region-handle {
  position: absolute;
  width: 14px;
  height: 14px;
  box-sizing: border-box;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.55);
  border-radius: 3px;
}

/* 命中区扩到 ~34px（键盘等价操作：Shift+方向键缩放） */
.fb-region-handle::after {
  content: "";
  position: absolute;
  inset: -10px;
}

.fb-region-handle[data-handle="nw"] { left: -7px; top: -7px; cursor: nwse-resize; }
.fb-region-handle[data-handle="n"] { left: 50%; top: -7px; margin-left: -7px; cursor: ns-resize; }
.fb-region-handle[data-handle="ne"] { right: -7px; top: -7px; cursor: nesw-resize; }
.fb-region-handle[data-handle="e"] { right: -7px; top: 50%; margin-top: -7px; cursor: ew-resize; }
.fb-region-handle[data-handle="se"] { right: -7px; bottom: -7px; cursor: nwse-resize; }
.fb-region-handle[data-handle="s"] { left: 50%; bottom: -7px; margin-left: -7px; cursor: ns-resize; }
.fb-region-handle[data-handle="sw"] { left: -7px; bottom: -7px; cursor: nesw-resize; }
.fb-region-handle[data-handle="w"] { left: -7px; top: 50%; margin-top: -7px; cursor: ew-resize; }

.fb-region-toolbar {
  position: absolute;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  z-index: 2;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
  max-width: calc(100vw - 32px);
  padding: 12px 16px;
  border-radius: 16px;
  background: rgba(28, 28, 30, 0.94);
  border: 1px solid rgba(255, 255, 255, 0.14);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.35);
  cursor: default;
}

.fb-region-size {
  font-size: 12px;
  color: #c7c7cc;
  font-variant-numeric: tabular-nums;
}

.fb-region-btn {
  min-height: 44px;
  padding: 0 16px;
  border-radius: 980px;
  border: 1px solid rgba(255, 255, 255, 0.24);
  background: transparent;
  color: #f5f5f7;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease;
}

.fb-region-btn:hover {
  background: rgba(255, 255, 255, 0.12);
}

.fb-region-use {
  background: var(--fb-accent);
  border-color: var(--fb-accent);
  color: #fff;
}

.fb-region-use:hover {
  background: var(--fb-accent-hover);
}

.fb-region-btn:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
}

.fb-region-error {
  position: absolute;
  left: 50%;
  bottom: 96px;
  transform: translateX(-50%);
  z-index: 2;
  margin: 0;
  max-width: calc(100vw - 32px);
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.4;
  color: #ff453a;
  background: rgba(255, 69, 58, 0.16);
  border: 1px solid rgba(255, 69, 58, 0.4);
}
`
), At = 48, Mi = 400, Ti = 260, mt = 8, se = ["nw", "n", "ne", "e", "se", "s", "sw", "w"];
function ne(i) {
  return Number.isFinite(i) ? Math.max(0, Math.min(1, i)) : 0.5;
}
function xt(i) {
  return Number.isFinite(i) ? Math.max(0, Math.floor(i * 1e6) / 1e6) : 0;
}
function re(i, t) {
  let e = i;
  return e > t && (e = Math.max(0, Math.floor(t * 1e6) / 1e6)), e > t && (e = Math.max(0, t - 1e-9)), e;
}
function lt(i, t) {
  const e = Math.max(1, t.width), s = Math.max(1, t.height), n = Math.min(At, e), r = Math.min(At, s), a = Math.max(n, Math.min(i.width, e)), o = Math.max(r, Math.min(i.height, s)), h = Math.max(0, Math.min(i.x, e - a)), d = Math.max(0, Math.min(i.y, s - o));
  return { x: h, y: d, width: a, height: o };
}
function Pi(i, t) {
  const e = Math.max(1, t.width), s = Math.max(1, t.height), n = Math.min(Mi, e * 0.8), r = Math.min(Ti, s * 0.4), a = ne(i.x) * e, o = ne(i.y) * s;
  return lt({ x: a - n / 2, y: o - r / 2, width: n, height: r }, t);
}
function oe(i, t, e, s) {
  return lt({ ...i, x: i.x + t, y: i.y + e }, s);
}
function ae(i, t, e, s, n) {
  const r = Math.max(1, n.width), a = Math.max(1, n.height), o = Math.min(At, r), h = Math.min(At, a);
  let d = i.x, c = i.y, u = i.x + i.width, f = i.y + i.height;
  return t.includes("w") && (d = Math.max(0, Math.min(d + e, u - o))), t.includes("e") && (u = Math.min(r, Math.max(u + e, d + o))), t.includes("n") && (c = Math.max(0, Math.min(c + s, f - h))), t.includes("s") && (f = Math.min(a, Math.max(f + s, c + h))), lt({ x: d, y: c, width: u - d, height: f - c }, n);
}
function le(i, t, e) {
  const s = Math.min(i.x, t.x), n = Math.min(i.y, t.y), r = Math.max(i.x, t.x), a = Math.max(i.y, t.y);
  return lt({ x: s, y: n, width: r - s, height: a - n }, e);
}
function ke(i, t) {
  const e = Math.max(1, t.width), s = Math.max(1, t.height), n = lt(i, t), r = xt(n.x / e), a = xt(n.y / s), o = re(xt(n.width / e), 1 - r), h = re(xt(n.height / s), 1 - a);
  return { x: r, y: a, width: o, height: h };
}
function Fi(i, t, e) {
  const s = Math.max(1, Math.round(t)), n = Math.max(1, Math.round(e)), r = Math.max(0, Math.min(s - 1, Math.round(i.x * s))), a = Math.max(0, Math.min(n - 1, Math.round(i.y * n))), o = Math.max(r + 1, Math.min(s, Math.round((i.x + i.width) * s))), h = Math.max(a + 1, Math.min(n, Math.round((i.y + i.height) * n)));
  return { x: r, y: a, width: o - r, height: h - a };
}
async function zi(i, t, e) {
  const s = await vt(i);
  try {
    const n = ke(t, e), r = Fi(n, s.width, s.height), a = document.createElement("canvas");
    a.width = r.width, a.height = r.height;
    const o = a.getContext("2d");
    if (!o) throw new Error("无法取得裁剪画布上下文");
    o.drawImage(s.source, r.x, r.y, r.width, r.height, 0, 0, r.width, r.height);
    const h = await Dt(a);
    if (!h) throw new Error("裁剪结果编码失败");
    return { blob: h.blob, width: h.width, height: h.height };
  } finally {
    s.close?.();
  }
}
async function Ui(i) {
  if (typeof FileReader < "u")
    return new Promise((r, a) => {
      const o = new FileReader();
      o.onload = () => r(o.result), o.onerror = a, o.readAsDataURL(i);
    });
  const t = await i.arrayBuffer(), e = new Uint8Array(t);
  let s = "";
  for (let r = 0; r < e.byteLength; r++)
    s += String.fromCharCode(e[r]);
  const n = typeof btoa == "function" ? btoa(s) : "";
  return `data:${i.type || "application/octet-stream"};base64,${n}`;
}
const Tt = {
  open: "待处理",
  waiting_user: "待补充",
  waiting_admin: "待管理员回复",
  resolved: "已解决"
}, yt = 2e3, Oi = 5e3, qi = 12e4, Pt = 3e4, Di = 864e5, he = "截取当前页面", Hi = "截取中…";
function de(i) {
  return i === "archived" || i === "failed" || i === "needs_review";
}
function Ft(i) {
  return i === void 0 || Number.isNaN(i) ? "" : i < 1024 ? `${i} B` : `${(i / 1024).toFixed(1)} KB`;
}
const Q = 3, ce = 1024 * 1024, ue = /* @__PURE__ */ new Set([".log", ".txt", ".json", ".jsonl"]);
function fe(i) {
  const t = i.lastIndexOf(".");
  return t === -1 ? "" : i.slice(t).toLowerCase();
}
function zt(i, t) {
  if (i.length !== t.length) return !1;
  for (let e = 0; e < i.length; e++) {
    const s = i[e], n = t[e];
    if (!s || !n || s.blob !== n.blob || s.filename !== n.filename || s.source !== n.source)
      return !1;
  }
  return !0;
}
function Ut(i, t) {
  if (i.length !== t.length) return !1;
  for (let e = 0; e < i.length; e++)
    if (i[e]?.id !== t[e]?.id || i[e]?.blob !== t[e]?.blob) return !1;
  return !0;
}
function Ot(i) {
  return {
    id: i.id,
    source: i.source,
    filename: i.filename,
    blob: i.blob
  };
}
async function _i(i) {
  return typeof i.text == "function" ? await i.text() : new Promise((t, e) => {
    const s = new FileReader();
    s.onload = () => t(s.result), s.onerror = () => e(s.error), s.readAsText(i, "utf-8");
  });
}
function l(i, t, e) {
  const s = document.createElement(i);
  return t && (s.className = t), e !== void 0 && (s.textContent = e), s;
}
function ge(i) {
  const t = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  t.setAttribute("class", i), t.setAttribute("viewBox", "0 0 20 20"), t.setAttribute("fill", "none"), t.setAttribute("stroke", "currentColor"), t.setAttribute("stroke-width", "1.6"), t.setAttribute("stroke-linecap", "round"), t.setAttribute("stroke-linejoin", "round"), t.setAttribute("aria-hidden", "true");
  const e = document.createElementNS("http://www.w3.org/2000/svg", "path");
  return e.setAttribute(
    "d",
    "M17 10.5a5.5 5.5 0 0 1-5.5 5.5c-1.1 0-2.1-.3-3-.9L4 16l.9-4.5A5.5 5.5 0 1 1 17 10.5z"
  ), t.append(e), t;
}
function Ni() {
  const i = l("span", "fb-orb-glow");
  i.setAttribute("aria-hidden", "true");
  const t = l("span", "fb-orb-core");
  return t.setAttribute("aria-hidden", "true"), { glow: i, core: t };
}
function qt() {
  const i = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  i.setAttribute("viewBox", "0 0 20 20"), i.setAttribute("width", "14"), i.setAttribute("height", "14"), i.setAttribute("fill", "none"), i.setAttribute("stroke", "currentColor"), i.setAttribute("stroke-width", "2"), i.setAttribute("stroke-linecap", "round"), i.setAttribute("aria-hidden", "true");
  const t = document.createElementNS("http://www.w3.org/2000/svg", "line");
  t.setAttribute("x1", "5"), t.setAttribute("y1", "5"), t.setAttribute("x2", "15"), t.setAttribute("y2", "15");
  const e = document.createElementNS("http://www.w3.org/2000/svg", "line");
  return e.setAttribute("x1", "15"), e.setAttribute("y1", "5"), e.setAttribute("x2", "5"), e.setAttribute("y2", "15"), i.append(t, e), i;
}
function $i() {
  const i = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  i.setAttribute("viewBox", "0 0 20 20"), i.setAttribute("width", "14"), i.setAttribute("height", "14"), i.setAttribute("fill", "none"), i.setAttribute("stroke", "currentColor"), i.setAttribute("stroke-width", "1.6"), i.setAttribute("stroke-linecap", "round"), i.setAttribute("aria-hidden", "true");
  const t = document.createElementNS("http://www.w3.org/2000/svg", "circle");
  t.setAttribute("cx", "10"), t.setAttribute("cy", "10"), t.setAttribute("r", "2.4"), i.append(t);
  for (const [e, s, n, r] of [
    [10, 2.2, 10, 4.4],
    [10, 15.6, 10, 17.8],
    [2.2, 10, 4.4, 10],
    [15.6, 10, 17.8, 10],
    [4.5, 4.5, 6.1, 6.1],
    [13.9, 13.9, 15.5, 15.5],
    [4.5, 15.5, 6.1, 13.9],
    [13.9, 6.1, 15.5, 4.5]
  ]) {
    const a = document.createElementNS("http://www.w3.org/2000/svg", "line");
    a.setAttribute("x1", String(e)), a.setAttribute("y1", String(s)), a.setAttribute("x2", String(n)), a.setAttribute("y2", String(r)), i.append(a);
  }
  return i;
}
class Bt extends HTMLElement {
  /** Shadow DOM 模式：默认 open；注册前可设 FeedbackWidget.shadowMode='closed'。 */
  static shadowMode = "open";
  static get observedAttributes() {
    return [
      "api-base",
      "app-id",
      "app-name",
      "app-version",
      "page-label",
      "side",
      "theme",
      "show-launcher",
      "launcher-bottom",
      "launcher-mode",
      "capture-mode"
    ];
  }
  root;
  launcher;
  orb;
  panel;
  metaInfo;
  /** 截图区 = 图片网格（有图才可见）+ 提示 + 操作区（没有截图时是「截取当前页面」）。 */
  shotArea;
  screenshotWrap;
  screenshotThumbBox;
  screenshotThumb;
  /** v12：手动添加图片入口与隐藏文件选择器。 */
  addImageBtn;
  imageFileInput;
  /** 预览旁提示：部分内容可能未入图 / 服务端单图降级。 */
  shotHintEl;
  /** 图片错误提示（类型不符、超限等）。 */
  shotErrorEl;
  /** 首个截图入口（无截图时显示）：默认 capture-mode=off 宿主唯一的手动截图方式。 */
  captureBtn;
  retakeBtn;
  removeBtn;
  zoomModal;
  zoomImg;
  zoomCloseBtn;
  textarea;
  counter;
  submitBtn;
  statusRegion;
  errorRegion;
  /** 今日剩余额度（登录后显示；未登录/未取得时隐藏）。 */
  quotaInfo;
  /** 额度用尽提示（含下次可提交时间）。 */
  quotaBlockedInfo;
  /** 面板内登录表单（点击「登录并提交」展开，不打开新窗口）。 */
  loginPanel;
  loginUsername;
  loginPassword;
  loginErrorEl;
  loginConfirmBtn;
  loginCancelBtn;
  /** T6：面板主体（设置视图展开时整体隐藏）。 */
  body;
  /** T6：服务器设置入口与视图。 */
  settingsBtn;
  settingsView;
  settingsInput;
  settingsCurrent;
  settingsDefaultRow;
  settingsDefaultText;
  settingsErrorEl;
  settingsHintEl;
  settingsSaveBtn;
  settingsRestoreBtn;
  settingsCancelBtn;
  settingsConfirmEl;
  settingsConfirmText;
  /** 日志附件区 */
  logsArea;
  logsCountEl;
  logsList;
  addLogBtn;
  logFileInput;
  logStatusEl;
  logErrorEl;
  /** 采集失败时的显式重试入口（挂在 logErrorEl 内，错误清空时随之移除）。 */
  logRetryBtn;
  logPreviewModal;
  logPreviewTitle;
  logPreviewBody;
  logPreviewCloseBtn;
  /** v11 对话与未读徽标 */
  launcherBadge;
  orbBadge;
  navTabs;
  tabComposeBtn;
  tabHistoryBtn;
  tabHistoryBadge;
  activeTab = "compose";
  unreadCount = 0;
  summaryPollTimer = null;
  /** 历史与对话视图 */
  historyContainer;
  historyListView;
  dialogueView;
  activeDialogueId = null;
  dialoguePollTimer = null;
  /** v12 我的反馈列表：当前页码与状态筛选。 */
  historyPage = 1;
  historyIssueStatus = "";
  /** 对话视图中已渲染到的最大消息 seq（增量追加用）。 */
  dialogueRenderedSeq = 0;
  /** Bearer 附件转出的对象 URL（对话关闭/重建时统一回收）。 */
  attachmentUrls = /* @__PURE__ */ new Set();
  /** 回复草稿图片（v12 多图，与新建反馈同一模型）。 */
  dialogueReplyImages = [];
  /** v12 能力：服务端声明的最大图片数；未确认时按多图乐观放行（登录后由会话校准）。 */
  maxImages = J;
  /** 服务端是否已明确返回 capabilities（缺 images → 单图模式 + 升级提示）。 */
  capabilitiesKnown = !1;
  /** features 探测在途标记（避免重复请求）。 */
  featuresProbing = !1;
  /** 当前打开的图片编辑器句柄（同时最多一个）。 */
  editorHandle = null;
  /** v12 诊断记录器：宿主可选注入；身份世代递增（服务/账号切换）时清空缓冲。 */
  _diagnostics;
  _diagnosticsFetch = null;
  get diagnosticsRecorder() {
    return this._diagnostics;
  }
  set diagnosticsRecorder(t) {
    this._diagnostics = t, this._diagnosticsFetch = null;
  }
  /** 组件内部请求使用的 fetch：注入记录器时经 wrapFetch 记录（白名单字段）。 */
  get apiFetch() {
    const t = this._diagnostics;
    return t ? (this._diagnosticsFetch || (this._diagnosticsFetch = Ai(t, (e, s) => fetch(e, s))), this._diagnosticsFetch) : fetch.bind(globalThis);
  }
  _logProvider;
  logsCollecting = !1;
  logTimeout = null;
  phase = "idle";
  polling = !1;
  pollTimer = null;
  pollDelay = yt;
  pollStartedAt = 0;
  openState = !1;
  authRequired = !1;
  /** 当前登录账号（登录响应提供；仅用于展示与额度归属）。 */
  authUser = null;
  /** 最近一次取得的每日额度；resetAt 之后重新查询，不在本地擅自重置。 */
  quota = null;
  loginVisible = !1;
  loginBusy = !1;
  loginError = "";
  visibilityHandler = null;
  /** 手动刷新服务端记录状态进行中（按钮禁用 + 防重入）。 */
  refreshing = !1;
  /**
   * T4：当前记录的「等待项」提示（等待配置 / 等待来源确认 / 等待人工归档）。
   * 非空表示记录已妥善保存、只是在等管理员操作——此时**不轮询**（等待不是处理中，
   * 无限轮询没有意义），改为提示 + 手动刷新；管理员处理后刷新即可看到结果。
   */
  waitingNotice = null;
  /**
   * 登录操作序号（T1-A）：每次发起登录递增；面板关闭 / 取消登录 /
   * 组件卸载 / 服务身份变化都会递增使在途登录失效，并清除密码、
   * 登录忙碌状态与待自动提交标记。
   *
   * 登录回调返回后必须同时满足「序号未变 + 身份世代未变 + 组件仍连接」，
   * 才允许写入令牌、更新界面或自动提交；失败回调同样校验，
   * 防止旧登录的错误覆盖新登录。由于 close() 也会递增序号，
   * 「序号仍有效」等价于「面板自登录发起以来没有被关闭」。
   */
  loginSeq = 0;
  /**
   * 额度查询序号（T1-B）：提交成功 / 收到额度错误 / 关闭 / 卸载 /
   * 退到后台 / 身份变化都会递增使在途查询失效；查询结果除校验身份、
   * 令牌外还要校验该序号，避免旧查询覆盖刚扣除后的次数。
   */
  quotaSeq = 0;
  /** 额度查询定时器（resetAt 单次 或 额度用尽后每 30 秒）；同时最多一个。 */
  quotaTimer = null;
  /** 额度查询进行中：同一时刻最多一个额度查询。 */
  quotaInFlight = !1;
  /** 最近一次额度刷新失败（网络等）：保留旧额度，30 秒后重试。 */
  quotaRefreshFailed = !1;
  /**
   * 「待立即刷新」标记（T1-B）：打开面板 / 回到前台的刷新被旧查询或登录
   * 忙碌挡下时登记，不得丢弃；旧查询结束后或忙碌结束后立即补发一次。
   * 多次登记合并为一次；关闭 / 卸载 / 退到后台 / 身份变化 / 401 时清除，
   * 重新打开按当前身份重新登记。
   */
  quotaRefreshPending = !1;
  /**
   * 服务身份世代（epoch）：`api-base` 或 `app-id` 变化时递增。
   * 每个异步操作在开始时捕获当前 epoch，恢复后先校验：
   * epoch 已变 → 该结果是旧服务 / 旧身份的，一律成为 no-op，
   * 绝不写入新身份的状态（提交响应、轮询记录、捕获会话、登录握手回调）。
   */
  identityEpoch = 0;
  /** 灵感球拖拽状态 */
  isDragging = !1;
  orbPointerId = null;
  isCapturing = !1;
  /** 进行中的捕获会话控制器：cancelCapture / 卸载时 abort，使旧会话失效。 */
  captureController = null;
  /** 捕获会话序号：新会话 / 失效操作递增；异步步骤后校验，旧会话结果一律丢弃。 */
  captureSeq = 0;
  /** 正在进行的捕获会话：普通呼出合并到该会话，不重复发起。 */
  captureInFlight = null;
  // ---------- T1 局部选区流程（拖球 → 松手 → 调整选区 → 确认） ----------
  /** 局部选区覆盖层与相关控件（在 buildDom 中构建，默认 hidden）。 */
  regionOverlay;
  regionBg;
  regionFrame;
  regionSizeEl;
  regionErrorEl;
  regionUseBtn;
  regionAllBtn;
  regionCancelBtn;
  /** 进行中的选区会话；null 表示覆盖层未打开。 */
  regionSession = null;
  /** 覆盖层自己的键盘监听（面板关闭时 keydownHandler 未注册，Esc 必须由它处理）。 */
  regionKeyHandler = null;
  /** T4：日志摘要区是否展开（默认只显示简短摘要）。 */
  logsOpen = !1;
  logsToggleBtn;
  logsSummaryEl;
  logsBody;
  recollectLogBtn;
  /**
   * 未提交草稿：组件实例所有（断开重连保留字节并重建自己的预览 URL），
   * 仅存内存，刷新即弃，绝不用 localStorage；appId 变化时整体废弃，
   * 旧捕获/旧草稿不得写入新身份。
   */
  draft = {
    text: "",
    images: [],
    logs: [],
    logsCollected: !1,
    version: 0
  };
  /** 当前提交快照（冻结）；失败且草稿未变时复用（同 key 同字节重试）。 */
  submitSnapshot = null;
  /** 结果未知的原提交请求：草稿被修改时保留供人工核对，不静默覆盖。 */
  unconfirmedRequest = null;
  /** 日志采集异步操作序号，防止迟到结果写入已切换的身份或草稿。 */
  logOpSeq = 0;
  invalidateLogCollection() {
    this.logOpSeq++, this.logsCollecting = !1, this.logTimeout !== null && (clearTimeout(this.logTimeout), this.logTimeout = null);
  }
  /**
   * 自定义日志提供者（L1-Web）：
   * 自动采集环境日志或诊断信息。返回单个或多个日志文件对象。
   */
  get logProvider() {
    return this._logProvider;
  }
  set logProvider(t) {
    this._logProvider = t, t && this.openState && !this.draft.logsCollected && this.draft.logs.length === 0 && this.collectLogs();
  }
  /**
   * 自定义截图提供者（扩展契约，实施计划 2.1）：
   * 保留原参数与返回值；ctx 新增可选 signal / viewport / sensitiveRegionCount，
   * 结果新增可选 viewport / sameFrameMasking / maskedRegions（输出像素坐标）。
   * 页面无可见敏感区域时旧式回调（只返回 {blob,width,height}）保持兼容。
   * 替换提供者（含置空）会使进行中的旧捕获会话失效。
   */
  _captureProvider;
  get captureProvider() {
    return this._captureProvider;
  }
  set captureProvider(t) {
    t !== this._captureProvider && this.invalidateCaptureSession(), this._captureProvider = t;
  }
  _sessionStore;
  _hostBridge;
  hostSessionLoading = !1;
  get hostBridge() {
    return this._hostBridge;
  }
  set hostBridge(t) {
    this._hostBridge = t, t?.sessionStore && this.loadHostSession();
  }
  get sessionStore() {
    return this._sessionStore ?? this._hostBridge?.sessionStore;
  }
  set sessionStore(t) {
    this._sessionStore = t, t && this.loadHostSession();
  }
  get isOpen() {
    return this.openState;
  }
  handleBackPressed() {
    return this.editorHandle && this.editorHandle.open ? (this.editorHandle.close(), this.editorHandle = null, !0) : this.isOpen ? (this.close(), !0) : !1;
  }
  openExternal(t) {
    if (this._hostBridge?.onOpenExternal)
      try {
        this._hostBridge.onOpenExternal(t);
        return;
      } catch {
      }
    typeof window < "u" && window.open(t, "_blank", "noopener,noreferrer");
  }
  async saveAttachment(t, e) {
    const s = new CustomEvent("feedback-save-attachment", {
      detail: t,
      bubbles: !0,
      composed: !0,
      cancelable: !0
    }), n = this.dispatchEvent(s);
    let r = !1;
    if (this._hostBridge?.onSaveAttachment) {
      r = !0;
      try {
        await this._hostBridge.onSaveAttachment(t);
      } catch {
      }
    }
    if (!r && n && !s.defaultPrevented && typeof document < "u") {
      const a = e ? URL.createObjectURL(e) : t.dataUrl;
      if (a) {
        e && this.attachmentUrls.add(a);
        const o = document.createElement("a");
        o.href = a, o.download = t.name, document.body.appendChild(o), o.click(), o.remove();
      }
    }
  }
  async loadHostSession() {
    if (this.hostSessionLoading) return;
    const t = this.sessionStore;
    if (t) {
      this.hostSessionLoading = !0;
      try {
        const e = await t.loadSession();
        if (!e || this.sessionStore !== t) return;
        const s = typeof e.accessToken == "string" ? e.accessToken.trim() : "", n = e.expiresAt, r = typeof n == "number" ? n : Date.parse(typeof n == "string" ? n : "");
        s && Number.isFinite(r) && (r > Date.now() ? this.adoptSession({ accessToken: s, expiresAt: n }) : Promise.resolve(t.clearSession()).catch(() => {
        }));
      } catch {
      } finally {
        this.hostSessionLoading = !1;
      }
    }
  }
  clearHostSession() {
    const t = this.sessionStore;
    if (t)
      try {
        Promise.resolve(t.clearSession()).catch(() => {
        });
      } catch {
      }
  }
  /**
   * T6：本机选择的自定义服务器覆盖（已规范化地址）；null 表示使用宿主 api-base。
   * 覆盖偏好按 localStorage 源 + appId + 规范化默认地址逐槽位隔离，
   * 仅在构造/身份属性变化时加载，不反写宿主配置。
   */
  serverOverride = null;
  /** T6：本机偏好是否可写（读取或写入失败置 false，设置视图据此提示「仅本次生效」）。 */
  serverPrefWritable = !0;
  /** T6：最近一次加载覆盖所用的偏好键——断开重连 / 重复调用不重复读取，避免吞掉「仅本次生效」的会话内选择。 */
  serverOverrideKeyLoaded = "";
  /** T6：设置视图是否展开（未登录也可进入）。 */
  settingsOpen = !1;
  /** T6：设置视图内的「确认切换」块是否展开（有草稿或结果未确认提交时先确认）。 */
  settingsConfirmOpen = !1;
  /** T6：等待确认的目标覆盖（null=恢复默认，undefined=无待确认切换）。 */
  pendingServerTarget = void 0;
  /** 令牌仅存组件实例内存；页面刷新后靠重新握手恢复。 */
  accessToken = null;
  tokenExpiresAt = 0;
  idempotencyKey = null;
  lastFeedbackId = null;
  lastSubmittedText = "";
  lastErrorSummary = null;
  lastRecord = null;
  keydownHandler = null;
  handledKey = null;
  constructor() {
    super(), this.root = this.attachShadow({ mode: Bt.shadowMode }), this.buildDom(), this.bindEvents(), this.syncUi();
  }
  // ---------- 公开 API ----------
  get apiBase() {
    return this.getAttribute("api-base");
  }
  set apiBase(t) {
    this.reflect("api-base", t);
  }
  /**
   * T6：当前实际使用的服务器地址（只读）：
   * 本机覆盖优先于宿主 `api-base`；未设置覆盖时返回宿主默认值。
   */
  get effectiveApiBase() {
    return this.serverOverride ?? this.apiBase;
  }
  get appId() {
    return this.getAttribute("app-id");
  }
  set appId(t) {
    this.reflect("app-id", t);
  }
  /**
   * 可选的软件名称（随提交上报）。
   * 服务端只在**管理员尚未设置名称**时采用；管理员设置过就绝不会被覆盖。
   */
  get appName() {
    return this.getAttribute("app-name");
  }
  set appName(t) {
    this.reflect("app-name", t);
  }
  get appVersion() {
    return this.getAttribute("app-version");
  }
  set appVersion(t) {
    this.reflect("app-version", t), this.updateMetaInfo();
  }
  get pageLabel() {
    return this.getAttribute("page-label");
  }
  set pageLabel(t) {
    this.reflect("page-label", t), this.updateMetaInfo();
  }
  get side() {
    return this.getAttribute("side") === "left" ? "left" : "right";
  }
  set side(t) {
    this.reflect("side", t === "left" ? "left" : "right");
  }
  get theme() {
    const t = this.getAttribute("theme");
    return t === "light" || t === "dark" ? t : "system";
  }
  set theme(t) {
    t === "system" ? this.removeAttribute("theme") : this.setAttribute("theme", t);
  }
  get showLauncher() {
    return this.getAttribute("show-launcher") !== "false";
  }
  set showLauncher(t) {
    t ? this.removeAttribute("show-launcher") : this.setAttribute("show-launcher", "false");
  }
  get launcherBottom() {
    return this.getAttribute("launcher-bottom") ?? "25%";
  }
  set launcherBottom(t) {
    this.reflect("launcher-bottom", t);
  }
  get launcherMode() {
    return this.getAttribute("launcher-mode") === "orb" ? "orb" : "tab";
  }
  set launcherMode(t) {
    t === "tab" ? this.removeAttribute("launcher-mode") : this.setAttribute("launcher-mode", "orb");
  }
  get captureMode() {
    return this.getAttribute("capture-mode") === "viewport" ? "viewport" : "off";
  }
  set captureMode(t) {
    t === "off" ? this.removeAttribute("capture-mode") : this.setAttribute("capture-mode", "viewport");
  }
  reflect(t, e) {
    e === null || e === "" ? this.removeAttribute(t) : this.setAttribute(t, e);
  }
  open() {
    if (this.openState) return;
    this.openState = !0, this.launcher.classList.add("is-hidden"), this.launcher.setAttribute("aria-expanded", "true"), this.orb.classList.add("is-hidden"), this.orb.setAttribute("aria-expanded", "true"), this.panel.classList.add("is-open"), this.panel.hidden = !1;
    const t = {};
    this.pageLabel && (t.page = this.pageLabel), this.dispatchEvent(
      new CustomEvent("feedback-panel-open", {
        detail: t,
        bubbles: !0,
        composed: !0
      })
    );
    try {
      this._hostBridge?.onPanelOpen?.(t);
    } catch {
    }
    this.updateAriaModal(), this.textarea.focus(), this.refreshQuota(), this.activeTab === "history" ? this.activeDialogueId ? (this.startDialoguePolling(), this.loadDialogue(this.activeDialogueId)) : (this.startSummaryPolling(), this.loadHistoryList()) : this.tokenValid() && this.phase !== "submitting" && this.phase !== "tracking" && this.startSummaryPolling(), this._logProvider && !this.draft.logsCollected && this.draft.logs.length === 0 && this.collectLogs(), this.keydownHandler = (e) => this.onKeydown(e), document.addEventListener("keydown", this.keydownHandler, !0), this.phase === "tracking" && (this.waitingNotice !== null ? this.refreshLastRecord() : this.resumePolling());
  }
  close() {
    if (this.stopDialoguePolling(), this.settingsOpen = !1, this.settingsConfirmOpen = !1, this.pendingServerTarget = void 0, this.invalidateLogin(), this.invalidateQuotaRefresh(), this.quotaRefreshPending = !1, this.invalidateLogCollection(), this.invalidateCaptureSession(), this.captureInFlight = null, this.restoreCaptureUi(), !!this.openState) {
      this.openState = !1, this.closeZoomModal(), this.closeLogPreview(), this.editorHandle?.close(), this.editorHandle = null, this.panel.classList.remove("is-open"), window.setTimeout(() => {
        this.openState || (this.panel.hidden = !0);
      }, 200), this.dispatchEvent(
        new CustomEvent("feedback-panel-close", {
          bubbles: !0,
          composed: !0
        })
      );
      try {
        this._hostBridge?.onPanelClose?.();
      } catch {
      }
      this.launcher.classList.remove("is-hidden"), this.launcher.setAttribute("aria-expanded", "false"), this.orb.classList.remove("is-hidden"), this.orb.setAttribute("aria-expanded", "false"), this.keydownHandler && (document.removeEventListener("keydown", this.keydownHandler, !0), this.keydownHandler = null), this.launcherMode === "orb" ? this.orb.focus() : this.launcher.focus();
    }
  }
  cancelCapture() {
    if (this.invalidateCaptureSession(), this.captureInFlight = null, this.orbPointerId !== null)
      try {
        this.orb.releasePointerCapture(this.orbPointerId);
      } catch {
      }
    this.isDragging = !1, this.orb.classList.remove("is-dragging"), this.orb.style.transform = "", this.orbPointerId = null, this.restoreCaptureUi();
  }
  /**
   * 使当前捕获会话失效：序号先行失效 → abort 取消；失效方负责恢复 UI
   * 与清理 in-flight（旧会话的 finally 不会再恢复新会话的 UI）。
   */
  invalidateCaptureSession() {
    this.captureSeq++, this.captureController?.abort(), this.captureController = null, this.captureInFlight = null, this.restoreCaptureUi();
  }
  /** 恢复被捕获流程隐藏的组件 UI（会话失效方负责恢复）。 */
  restoreCaptureUi() {
    this.panel.style.visibility = "", this.launcher.style.visibility = "", this.orb.style.visibility = "", this.isCapturing = !1, this.dismissRegionFlow(), this.syncShotUi();
  }
  /** prefers-reduced-motion：灵感球取消光晕流动与缩放。 */
  prefersReducedMotion() {
    try {
      return typeof window < "u" && typeof window.matchMedia == "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    } catch {
      return !1;
    }
  }
  /**
   * 灵感球拖拽松手 → 局部选区流程（T1）：
   * - 图片已达上限且无可替换截图：先提示整理附件，**不发起捕获**；
   * - 已有捕获会话在途：合并到该会话，不重复发起；
   * - 捕获失败沿用现有失败提示（草稿与旧图保留，绝不静默回退成整屏截图）。
   */
  async startRegionCapture(t) {
    if (this.captureInFlight) return this.captureInFlight;
    if (this.phase === "submitting" || this.polling || this.regionSession) return;
    if (!this.captureImage() && this.draft.images.length >= this.maxImages) {
      this.openState || this.open(), this.showImageError(`已达图片上限 ${this.maxImages} 张，请先删除一张图片再截图。`), this.renderStatus(`图片已达上限 ${this.maxImages} 张，先整理附件后再局部截图。`), this.syncUi();
      return;
    }
    const e = this.runCapture({ releasePoint: t.releasePoint, regionFlow: !0 });
    this.captureInFlight = e;
    try {
      await e;
    } finally {
      this.captureInFlight === e && (this.captureInFlight = null);
    }
  }
  /** 草稿是否已有内容（有文字、图片或日志的草稿再次呼出时恢复草稿、不重拍）。 */
  isDraftDirty() {
    return this.draft.text.trim().length > 0 || this.draft.images.length > 0 || this.draft.logs.length > 0;
  }
  /** 草稿中由截图产生的图片（重拍 / 移除截图的目标；编辑过的截图仍算）。 */
  captureImage() {
    return this.draft.images.find((t) => t.origin === "capture") ?? null;
  }
  /** 组件自身 UI 不参与截图，也不计入宿主页面敏感区域。 */
  isOwnFeedbackUi(t) {
    if (t === this || t.closest("feedback-widget") !== null) return !0;
    let e = t;
    for (; e; ) {
      const s = e.getRootNode();
      if (s instanceof ShadowRoot) {
        if (s.host === this || s.host.tagName.toLowerCase() === "feedback-widget") return !0;
        e = s.host;
      } else
        break;
    }
    return !1;
  }
  /**
   * 普通呼出（launcher / orb 点击 / 拖拽落点）：
   * - 已有草稿（文字或截图）→ 恢复草稿打开面板，不重拍；
   * - 正在捕获 → 合并到进行中的会话，不重复发起；
   * - 提交进行中 → 禁止编辑类操作，直接忽略。
   */
  async captureAndOpen(t = {}) {
    if (this.captureInFlight) return this.captureInFlight;
    if (this.phase === "submitting") return;
    if (this.isDraftDirty()) {
      this.openState || this.open(), this.syncUi();
      return;
    }
    const e = this.runCapture(t);
    this.captureInFlight = e;
    try {
      await e;
    } finally {
      this.captureInFlight === e && (this.captureInFlight = null);
    }
  }
  /**
   * 重拍（内部明确入口）：可取消旧会话后重新捕获；只有重拍替换旧截图，
   * 且失败不丢旧图（runCapture 失败路径不触碰草稿）。
   */
  async retakeScreenshot() {
    if (this.phase === "submitting") return;
    this.invalidateCaptureSession(), this.captureInFlight = null;
    const t = this.runCapture({});
    this.captureInFlight = t;
    try {
      await t;
    } finally {
      this.captureInFlight === t && (this.captureInFlight = null);
    }
  }
  /**
   * 面板内的手动截图入口（首次截图与重拍共用同一条路径）。
   *
   * 刻意**不**走 `captureAndOpen()`：那条路径是「呼出」语义——草稿一旦有内容
   * （哪怕只有文字）就只恢复草稿开面板、绝不重拍，于是默认 `capture-mode="off"`
   * 的宿主在用户先输入文字后，就再没有任何补拍入口（本次修复的缺口）。
   * 这里复用显式重拍流程：可替换旧图，失败不丢旧图与文字。
   *
   * 按钮在捕获期间已禁用；这里再挡一次重入（禁用态只是视觉 / a11y 保证，
   * 程序化调用与键盘连击不该产生并发会话）。
   */
  captureFromPanel() {
    if (this.phase === "submitting" || this.polling || this.isCapturing) return;
    const t = this.root.activeElement;
    this.retakeScreenshot().finally(() => this.restorePanelFocus(t));
  }
  /**
   * 捕获期间面板被 `visibility: hidden` 隐藏，真实浏览器会把焦点丢到 body
   * （面板内的按钮 / textarea 全部失焦）。两种情况要把焦点还回去：
   * 焦点已经不在面板内（`null`），或**卡在一个已经隐藏的控件上**
   * （个别引擎不主动移除隐藏元素的焦点）。优先点击前的元素（若它已隐藏 /
   * 禁用则回到 textarea），键盘用户不会在截图后凭空失去落点；
   * 用户已主动聚焦到可见的别处时绝不抢焦点。
   */
  restorePanelFocus(t) {
    if (!this.openState) return;
    const e = this.root.activeElement, s = e !== null && (e.hidden || e.closest("[hidden]") !== null);
    if (e !== null && !s) return;
    (t !== null && t.isConnected && !t.hidden && !t.disabled && t ? t : this.textarea).focus();
  }
  /**
   * 图片区同步（v12 多图）：**预览网格与操作分离**。
   * - 网格只在真的有可渲染对象 URL 时出现：绝不留下空 src 的破图占位
   *   （历史故障与其成因见 styles.ts 的 `[hidden]` 兜底注释）。
   * - 首个单元格是稳定 DOM（legacy .fb-screenshot-thumb-box/.fb-screenshot-thumb），
   *   其余单元格按 images[1..] 重建；每张图可放大、编辑、删除。
   * - 无截图 → 只显示「截取当前页面」；有截图 → 「重新截图 / 移除截图」；
   *   「添加图片」在未满上限（capabilities.images 或 5）时可用；服务端
   *   明确不支持多图时提示「升级服务端以支持多图」。
   * - 提交、轮询与捕获会话进行中，所有图片操作一律禁用（防重入）。
   */
  syncShotUi() {
    if (!this.shotArea) return;
    const t = this.draft.images, s = this.captureImage() !== null, n = this.phase === "submitting" || this.polling || this.isCapturing, r = t.length >= this.maxImages;
    this.screenshotWrap.hidden = t.length === 0;
    const a = t[0] ?? null;
    if (this.screenshotThumbBox.hidden = a === null, a) {
      this.screenshotThumb.src = a.url, this.screenshotThumb.alt = `图片 1：${a.filename}`;
      const h = this.screenshotThumbBox.querySelector(".fb-screenshot-badge");
      h && (h.textContent = this.imageBadgeText(a));
      const d = this.screenshotThumbBox.querySelector(".fb-screenshot-size");
      d && (d.textContent = this.imageSizeText(a));
      const c = this.screenshotThumbBox.querySelector(".fb-thumb-remove");
      c && (c.disabled = n, c.setAttribute("aria-label", "删除图片 1"));
      const u = this.screenshotThumbBox.querySelector(".fb-thumb-edit");
      u && (u.disabled = n, u.setAttribute("aria-label", "编辑图片 1"));
    } else
      this.screenshotThumb.removeAttribute("src");
    this.screenshotThumbBox.tabIndex = a === null ? -1 : 0;
    for (const h of Array.from(this.screenshotWrap.querySelectorAll(".fb-image-cell")))
      h.remove();
    for (let h = 1; h < t.length; h++)
      this.screenshotWrap.append(this.buildImageCell(t[h], h, n));
    this.captureBtn.hidden = s || r, this.retakeBtn.hidden = !s, this.removeBtn.hidden = !s, this.addImageBtn.hidden = r, this.captureBtn.disabled = n, this.retakeBtn.disabled = n, this.removeBtn.disabled = n, this.addImageBtn.disabled = n;
    const o = [];
    t.some((h) => h.hasUndrawable) && o.push("部分内容可能未入图（跨源嵌入内容无法被截取），可手动添加图片补充。"), t.some((h) => h.oversize) && o.push("有图片缩小后仍超过 5MiB，提交可能被服务端拒绝。"), this.capabilitiesKnown && this.maxImages < J && o.push("当前服务端仅支持单张图片，升级服务端以支持多图。"), this.shotHintEl.textContent = o.join(" "), this.shotHintEl.hidden = o.length === 0, this.captureBtn.textContent = this.isCapturing ? Hi : he, this.isCapturing ? this.captureBtn.setAttribute("aria-busy", "true") : this.captureBtn.removeAttribute("aria-busy"), this.shotArea.classList.toggle("is-capturing", this.isCapturing);
  }
  /**
   * 图片角标文案（T2.5）：局部截图（带 region）/ 截图 / 图片 + 编辑标记。
   * 编辑过的图仍带「已编辑」；带 region 的截图标「局部截图」。
   */
  imageBadgeText(t) {
    const e = t.origin === "capture" ? t.captureInfo?.region ? "局部截图" : "截图" : "图片";
    return t.source !== "edited" ? e : t.origin === "capture" ? `${e}·已编辑` : "已编辑";
  }
  /** 图片尺寸读数（输出像素，宽×高）。 */
  imageSizeText(t) {
    return `${t.width}×${t.height}`;
  }
  /** 构建第 index 张图片的网格单元格（index≥1 的次要单元格，重建型）。 */
  buildImageCell(t, e, s) {
    const n = l("div", "fb-image-cell fb-screenshot-thumb-box");
    n.setAttribute("role", "button"), n.setAttribute("tabindex", "0"), n.setAttribute("aria-label", `查看图片 ${e + 1}`);
    const r = l("div", "fb-thumb-frame"), a = l("img", "fb-screenshot-thumb");
    a.src = t.url, a.alt = `图片 ${e + 1}：${t.filename}`;
    const o = l("span", "fb-screenshot-badge", this.imageBadgeText(t)), h = l("span", "fb-screenshot-size", this.imageSizeText(t)), d = l("span", "fb-screenshot-zoom-hint", "点击放大");
    r.append(a, o, h, d);
    const c = l("div", "fb-thumb-actions"), u = l("button", "fb-thumb-edit", "编辑");
    u.type = "button", u.disabled = s, u.setAttribute("aria-label", `编辑图片 ${e + 1}`), u.addEventListener("click", (x) => {
      x.stopPropagation(), this.openEditorForImage(t.id);
    });
    const f = l("button", "fb-thumb-remove", "删除");
    return f.type = "button", f.disabled = s, f.setAttribute("aria-label", `删除图片 ${e + 1}`), f.addEventListener("click", (x) => {
      x.stopPropagation(), this.removeImage(t.id);
    }), c.append(u, f), n.append(r, c), n.addEventListener("click", () => this.openZoomModal(t.url)), n.addEventListener("keydown", (x) => {
      (x.key === " " || x.key === "Enter") && (x.preventDefault(), this.openZoomModal(t.url));
    }), n;
  }
  /** 图片错误提示（shotErrorEl）。 */
  showImageError(t) {
    this.shotErrorEl.textContent = t, this.shotErrorEl.hidden = t === "";
  }
  /**
   * 把文件加入草稿图片列表（选择 / 拖入 / 粘贴共用）。
   * 校验 → 解码 → 需要时等比缩小；全部失败给出可读错误，部分成功保留成功的。
   * 数量超过 maxImages（capabilities 或 5）时丢弃超出的并提示。
   */
  async addImageFiles(t, e = "image") {
    if (this.phase === "submitting" || this.polling) return;
    const s = this.identityEpoch;
    this.showImageError("");
    const n = Array.from(t).filter((r) => r && r.size !== void 0);
    if (n.length !== 0) {
      for (const r of n) {
        if (s !== this.identityEpoch) return;
        if (this.draft.images.length >= this.maxImages) {
          this.showImageError(`最多附加 ${this.maxImages} 张图片`);
          break;
        }
        const a = r instanceof File ? r.name : "";
        try {
          const o = await Xt(r);
          if (s !== this.identityEpoch) return;
          this.pushDraftImage(o, "manual", pt(a, o.mime, `${e}-${this.draft.images.length + 1}${o.mime === "image/png" ? ".png" : o.mime === "image/jpeg" ? ".jpg" : ".webp"}`));
        } catch (o) {
          if (s !== this.identityEpoch) return;
          const h = a || "所选图片";
          o instanceof G ? o.reason === "invalid_type" ? this.showImageError(`${h}：仅支持 PNG / JPEG / WebP 静态图片`) : o.reason === "empty" ? this.showImageError(`${h}：不能为空文件`) : this.showImageError(`${h}：图片无法读取`) : this.showImageError(`${h}：图片无法读取`);
        }
      }
      this.syncUi();
    }
  }
  /** 新增一张草稿图片（manual 来源；截图走 capture 路径）。 */
  pushDraftImage(t, e, s) {
    const n = {
      id: ft(),
      blob: t.blob,
      url: URL.createObjectURL(t.blob),
      source: e,
      origin: "manual",
      filename: s,
      mime: t.mime,
      width: t.width,
      height: t.height,
      oversize: t.oversize
    };
    return this.draft.images.push(n), this.draft.version++, n;
  }
  /** 按 ID 删除一张草稿图片（释放对象 URL）。 */
  removeImage(t) {
    if (this.phase === "submitting" || this.polling) return;
    const e = this.draft.images.findIndex((n) => n.id === t);
    if (e === -1) return;
    const [s] = this.draft.images.splice(e, 1);
    s?.url && URL.revokeObjectURL(s.url), this.draft.version++, this.syncUi();
  }
  /**
   * 打开全屏编辑器编辑指定图片：保存结果经统一限额收缩后**原位替换**
   * （同 ID、不占新名额），来源标记为 'edited'；取消不改动草稿。
   */
  async openEditorForImage(t) {
    if (this.phase === "submitting" || this.polling || this.editorHandle) return;
    const e = this.draft.images.find((r) => r.id === t);
    if (!e) return;
    const s = this.identityEpoch;
    try {
      const r = await vt(e.blob);
      if (s !== this.identityEpoch) return;
      const a = e;
      this.editorHandle = ie({
        image: r.source,
        width: r.width,
        height: r.height,
        mount: this.root,
        onCancel: () => {
          this.editorHandle = null, r.close?.();
        },
        onSave: async (o) => {
          const h = await Dt(o);
          if (!(s !== this.identityEpoch || !n(this, a))) {
            if (!h) {
              this.showImageError("图片编辑结果无法导出");
              return;
            }
            URL.revokeObjectURL(a.url), a.blob = h.blob, a.url = URL.createObjectURL(h.blob), a.source = "edited", a.mime = h.mime, a.width = h.width, a.height = h.height, a.oversize = h.oversize, a.filename = pt(a.filename, h.mime, `edited-${a.id}.png`), this.draft.version++, this.editorHandle?.close(), this.editorHandle = null, r.close?.(), this.syncUi();
          }
        }
      }), this.syncUi();
    } catch {
      s === this.identityEpoch && this.showImageError("图片无法打开进行编辑");
    }
    function n(r, a) {
      return r.isConnected && r.draft.images.includes(a);
    }
  }
  /**
   * 单次捕获会话主体：每个异步步骤后与更新草稿 / 开面板前都校验会话有效性。
   *
   * `regionFlow=true`（灵感球拖拽）时，捕获成功**不写草稿**：位图被冻结并交给
   * 局部选区覆盖层，用户确认（使用此区域 / 截取整个窗口）后才裁剪并 commit，
   * 取消则整体丢弃；失败路径与直接路径完全一致（提示失败、保留草稿与旧图）。
   */
  async runCapture(t) {
    const e = ++this.captureSeq, s = this.identityEpoch, n = () => e === this.captureSeq && s === this.identityEpoch, r = new AbortController();
    this.captureController = r;
    const { signal: a } = r;
    this.isCapturing = !0, this.syncShotUi();
    const o = this.openState;
    this.panel.style.visibility = "hidden", this.launcher.style.visibility = "hidden", this.orb.style.visibility = "hidden";
    let h = null, d = null;
    try {
      if (a.aborted || !n()) throw new w("aborted", "截图会话已失效");
      if (typeof window > "u") throw new w("locate-failed", "无可用 window 环境");
      const c = ei(window), u = ti(document, {
        ignore: (B) => this.isOwnFeedbackUi(B)
      }), f = Xe(document, (B) => this.isOwnFeedbackUi(B));
      let x, b, m, A, v;
      if (this._captureProvider) {
        const B = await this._captureProvider({
          releasePoint: t.releasePoint,
          signal: a,
          viewport: c,
          sensitiveRegionCount: u
        });
        if (a.aborted || !n()) throw new w("aborted", "截图会话已失效");
        const k = Ze(B, u);
        x = k.blob, b = k.viewportWidth, m = k.viewportHeight, A = k.outputWidth, v = k.outputHeight;
      } else {
        const B = await ci({
          viewport: c,
          expectedSensitiveCount: u,
          signal: a,
          ignore: (k) => k === this || k.tagName.toLowerCase() === "feedback-widget"
        });
        if (a.aborted || !n()) throw new w("aborted", "截图会话已失效");
        x = B.blob, b = B.viewportWidth, m = B.viewportHeight, A = B.outputWidth, v = B.outputHeight;
      }
      if (!n()) throw new w("aborted", "截图会话已失效");
      !this.captureImage() && this.draft.images.length >= this.maxImages ? h = `已达图片上限 ${this.maxImages} 张，新截图未加入；可先删除一张图片再截图。` : (d = {
        blob: x,
        viewportWidth: b,
        viewportHeight: m,
        pixelWidth: A,
        pixelHeight: v,
        capturedAt: (/* @__PURE__ */ new Date()).toISOString(),
        ...t.releasePoint ? { releasePoint: t.releasePoint } : {},
        undrawableCount: f
      }, t.regionFlow || this.commitCapture(d));
    } catch (c) {
      c instanceof w && c.reason === "aborted" || (h = c instanceof w ? c.userMessage : ye, console.warn("[Feedback] Screenshot capture failed:", c));
    } finally {
      n() && (this.captureController === r && (this.captureController = null), this.restoreCaptureUi());
    }
    if (n()) {
      if (d && t.regionFlow) {
        this.openRegionFlow(d), this.syncUi();
        return;
      }
      o || this.open(), this.syncUi(), h && this.renderStatus(h);
    }
  }
  /**
   * 把一次已通过校验的冻结捕获写入草稿（直接路径与选区确认路径共用）：
   * 重拍原位替换（同 ID、不占名额），首次则新增；`region` 仅在局部截图时写入
   * `captureInfo`，整窗截图省略该字段（与旧客户端一致）。
   */
  commitCapture(t, e) {
    const s = {
      viewportWidth: t.viewportWidth,
      viewportHeight: t.viewportHeight,
      pixelWidth: t.pixelWidth,
      pixelHeight: t.pixelHeight,
      capturedAt: t.capturedAt,
      ...t.releasePoint ? { releasePoint: t.releasePoint } : {},
      ...e ? { region: e } : {}
    }, n = this.captureImage();
    if (n) {
      URL.revokeObjectURL(n.url), n.blob = t.blob, n.url = URL.createObjectURL(t.blob), n.source = "capture", n.mime = "image/png", n.width = t.pixelWidth, n.height = t.pixelHeight, n.filename = "screenshot.png", n.captureInfo = s, n.hasUndrawable = t.undrawableCount > 0, n.oversize = !1, this.draft.version++;
      return;
    }
    this.draft.images.push({
      id: ft(),
      blob: t.blob,
      url: URL.createObjectURL(t.blob),
      source: "capture",
      origin: "capture",
      filename: "screenshot.png",
      mime: "image/png",
      width: t.pixelWidth,
      height: t.pixelHeight,
      captureInfo: s,
      hasUndrawable: t.undrawableCount > 0
    }), this.draft.version++;
  }
  // ---------- T1 局部选区覆盖层（拖球 → 松手 → 调整选区 → 确认） ----------
  /** 打开覆盖层：冻结位图铺满显示，初始选区以松手位置为中心并整体移回可见区。 */
  openRegionFlow(t) {
    this.regionSession && this.dismissRegionFlow({ focusOrb: !1 });
    const e = {
      width: Math.max(1, t.viewportWidth),
      height: Math.max(1, t.viewportHeight)
    }, s = {
      width: Math.max(1, typeof window < "u" && window.innerWidth || e.width),
      height: Math.max(1, typeof window < "u" && window.innerHeight || e.height)
    }, n = t.releasePoint ?? { x: 0.5, y: 0.5 }, r = {
      epoch: this.identityEpoch,
      frozen: t,
      url: URL.createObjectURL(t.blob),
      viewport: e,
      overlay: s,
      sel: Pi(n, e),
      dragging: null
    };
    this.regionSession = r, this.regionBg.src = r.url, this.regionOverlay.hidden = !1, this.regionErrorEl.hidden = !0, this.regionErrorEl.textContent = "", this.regionKeyHandler = (a) => this.onRegionKeydown(a), document.addEventListener("keydown", this.regionKeyHandler, !0), this.renderRegion(), this.regionFrame.focus();
  }
  /** 覆盖层显示尺寸 ↔ 捕获视口逻辑像素的换算（位图铺满覆盖层显示）。 */
  regionScale(t) {
    return {
      kx: t.overlay.width / t.viewport.width,
      ky: t.overlay.height / t.viewport.height
    };
  }
  /** 指针事件 → 选区坐标系（捕获视口逻辑像素）。 */
  regionPoint(t, e) {
    const { kx: s, ky: n } = this.regionScale(t);
    return { x: e.clientX / s, y: e.clientY / n };
  }
  /** 重绘选区框（位置 / 尺寸 / 输出尺寸读数）：覆盖层打开期间持续生效。 */
  renderRegion() {
    const t = this.regionSession;
    if (!t) return;
    const { kx: e, ky: s } = this.regionScale(t);
    this.regionFrame.style.left = `${t.sel.x * e}px`, this.regionFrame.style.top = `${t.sel.y * s}px`, this.regionFrame.style.width = `${t.sel.width * e}px`, this.regionFrame.style.height = `${t.sel.height * s}px`, this.regionSizeEl.textContent = `选区 ${Math.round(t.sel.width)}×${Math.round(t.sel.height)} px`;
  }
  onRegionPointerDown(t) {
    const e = this.regionSession;
    if (!e || t.button !== 0) return;
    const s = t.target;
    if (s && typeof s.closest == "function" && s.closest(".fb-region-toolbar"))
      return;
    const n = this.regionPoint(e, t), a = (s && typeof s.closest == "function" ? s.closest("[data-handle]") : null)?.getAttribute("data-handle");
    a && se.includes(a) ? e.dragging = { mode: "resize", handle: a, startX: n.x, startY: n.y, startSel: { ...e.sel } } : s && this.regionFrame.contains(s) ? e.dragging = { mode: "move", startX: n.x, startY: n.y, startSel: { ...e.sel } } : (e.dragging = { mode: "new", startX: n.x, startY: n.y, startSel: { ...e.sel } }, e.sel = le(n, n, e.viewport));
    try {
      this.regionOverlay.setPointerCapture(t.pointerId);
    } catch {
    }
    this.renderRegion(), t.preventDefault();
  }
  onRegionPointerMove(t) {
    const e = this.regionSession;
    if (!e || !e.dragging) return;
    const s = e.dragging, n = this.regionPoint(e, t);
    s.mode === "move" ? e.sel = oe(s.startSel, n.x - s.startX, n.y - s.startY, e.viewport) : s.mode === "resize" ? e.sel = ae(s.startSel, s.handle, n.x - s.startX, n.y - s.startY, e.viewport) : e.sel = le({ x: s.startX, y: s.startY }, n, e.viewport), this.renderRegion();
  }
  onRegionPointerEnd(t) {
    const e = this.regionSession;
    if (!(!e || !e.dragging)) {
      e.dragging = null;
      try {
        this.regionOverlay.releasePointerCapture(t.pointerId);
      } catch {
      }
      this.renderRegion();
    }
  }
  /**
   * 确认：`useSelection=true` 裁剪冻结位图到选区（region 随元数据透传），
   * false 使用整张冻结位图（region 省略，与旧客户端一致）。
   * 裁剪失败保持覆盖层打开并给出可重试提示，绝不静默回退成整屏截图。
   */
  async confirmRegion(t) {
    const e = this.regionSession;
    if (!e) return;
    if (e.epoch !== this.identityEpoch) {
      this.dismissRegionFlow();
      return;
    }
    let s = e.frozen.blob, n = e.frozen.pixelWidth, r = e.frozen.pixelHeight, a;
    if (t)
      try {
        const h = await zi(e.frozen.blob, e.sel, e.viewport);
        s = h.blob, n = h.width, r = h.height, a = ke(e.sel, e.viewport);
      } catch {
        if (this.regionSession !== e || e.epoch !== this.identityEpoch) return;
        this.regionErrorEl.textContent = "裁剪未完成，可重试，或改用「截取整个窗口」。", this.regionErrorEl.hidden = !1;
        return;
      }
    if (this.regionSession !== e || e.epoch !== this.identityEpoch) return;
    const o = { ...e.frozen, blob: s, pixelWidth: n, pixelHeight: r };
    this.dismissRegionFlow({ focusOrb: !1 }), this.commitCapture(o, a), this.openState || this.open(), this.syncUi(), this.renderStatus(t ? "已附加局部截图" : "已附加整窗截图");
  }
  /** 取消：丢弃冻结画面并恢复 UI，不覆盖原草稿、不报失败；焦点回到灵感球。 */
  cancelRegion() {
    this.dismissRegionFlow();
  }
  /** 收起覆盖层（任何失效路径共用）：回收对象 URL、移除监听、还原焦点。 */
  dismissRegionFlow(t = {}) {
    const e = this.regionSession;
    if (e) {
      this.regionSession = null, this.regionKeyHandler && (document.removeEventListener("keydown", this.regionKeyHandler, !0), this.regionKeyHandler = null), this.regionOverlay.hidden = !0, this.regionBg.removeAttribute("src");
      try {
        URL.revokeObjectURL(e.url);
      } catch {
      }
      if (this.regionErrorEl.hidden = !0, this.regionErrorEl.textContent = "", t.focusOrb !== !1 && this.isConnected)
        try {
          this.orb.focus();
        } catch {
        }
    }
  }
  /** 覆盖层键盘：Esc=取消、回车=使用此区域、方向键=移动、Shift+方向键=缩放、Tab 焦点锁。 */
  onRegionKeydown(t) {
    const e = this.regionSession;
    if (!e) return;
    if (t.key === "Escape") {
      t.preventDefault(), t.stopPropagation(), this.cancelRegion();
      return;
    }
    if (t.key === "Enter") {
      t.preventDefault(), t.stopPropagation(), this.confirmRegion(!0);
      return;
    }
    const s = t.key === "ArrowLeft" ? -mt : t.key === "ArrowRight" ? mt : 0, n = t.key === "ArrowUp" ? -mt : t.key === "ArrowDown" ? mt : 0;
    if (s !== 0 || n !== 0) {
      const r = this.root.activeElement;
      if (r !== this.regionFrame && r !== this.regionOverlay) return;
      if (t.preventDefault(), t.shiftKey) {
        const a = s !== 0 ? s > 0 ? "e" : "w" : n > 0 ? "s" : "n";
        e.sel = ae(e.sel, a, s, n, e.viewport);
      } else
        e.sel = oe(e.sel, s, n, e.viewport);
      this.renderRegion();
      return;
    }
    if (t.key === "Tab") {
      const r = Array.from(
        this.regionOverlay.querySelectorAll('button:not([disabled]), [tabindex="0"]')
      ).filter((o) => o.closest("[hidden]") === null);
      if (r.length === 0) return;
      const a = r.indexOf(this.root.activeElement);
      t.preventDefault(), t.shiftKey ? (a <= 0 ? r[r.length - 1] : r[a - 1])?.focus() : (a === -1 || a === r.length - 1 ? r[0] : r[a + 1])?.focus();
    }
  }
  openZoomModal(t) {
    const e = t ?? this.draft.images[0]?.url;
    e && (this.zoomImg.src = e, this.zoomModal.classList.add("is-open"));
  }
  closeZoomModal() {
    this.zoomModal && this.zoomModal.classList.remove("is-open");
  }
  /** 移除截图（origin=capture 的那一张；手动图片不受影响），并递增草稿版本。 */
  removeScreenshot() {
    if (this.phase === "submitting" || this.polling) return;
    const t = this.captureImage();
    t && this.removeImage(t.id);
  }
  /** 清空草稿（提交成功后 / appId 变化时），字节与预览 URL 一并释放。 */
  clearDraft() {
    this.draft.text = "";
    for (const t of this.draft.images)
      t.url && URL.revokeObjectURL(t.url);
    this.draft.images = [], this.draft.logs = [], this.draft.logsCollected = !1, this.draft.version++;
  }
  isMobile() {
    return window.matchMedia("(max-width: 767.98px)").matches;
  }
  updateAriaModal() {
    this.panel.setAttribute("aria-modal", this.isMobile() ? "true" : "false");
  }
  // ---------- 生命周期 ----------
  /** 一次性事件绑定（构造期）：重挂载绝不重复绑定。 */
  bindEvents() {
    this.launcher.addEventListener("click", () => {
      this.openState ? this.close() : this.captureMode === "viewport" ? this.captureAndOpen() : this.open();
    }), this.initOrbGesture(), this.textarea.addEventListener("input", () => {
      this.draft.text = this.textarea.value, this.draft.version++, (this.phase === "archived" || this.phase === "needs_review") && (this.phase = "idle"), this.syncUi();
    }), this.textarea.addEventListener("paste", (t) => {
      const e = Array.from(t.clipboardData?.files ?? []).filter(
        (s) => /^image\//.test(s.type)
      );
      e.length !== 0 && (t.preventDefault(), this.addImageFiles(e, "paste"));
    }), this.body.addEventListener("dragover", (t) => {
      t.dataTransfer && Array.from(t.dataTransfer.types).includes("Files") && (t.preventDefault(), t.dataTransfer.dropEffect = "copy", this.body.classList.add("is-dragover"));
    }), this.body.addEventListener("dragleave", (t) => {
      t.relatedTarget && this.body.contains(t.relatedTarget) || this.body.classList.remove("is-dragover");
    }), this.body.addEventListener("drop", (t) => {
      this.body.classList.remove("is-dragover");
      const e = Array.from(t.dataTransfer?.files ?? []).filter(
        (s) => /^image\//.test(s.type)
      );
      e.length !== 0 && (t.preventDefault(), this.addImageFiles(e, "drop"));
    }), this.root.addEventListener("click", (t) => {
      if (!this._hostBridge?.onOpenExternal) return;
      const e = t.composedPath();
      for (const s of e)
        if (s instanceof Element && s.tagName.toLowerCase() === "a") {
          const n = s;
          if (n.href && !n.hasAttribute("download") && (n.target === "_blank" || /^https?:\/\//i.test(n.getAttribute("href") || ""))) {
            t.preventDefault(), this.openExternal(n.href);
            break;
          }
        }
    }), this.submitBtn.addEventListener("click", () => {
      this.onPrimaryAction();
    });
  }
  connectedCallback() {
    this.hasAttribute("side") || this.setAttribute("side", "right"), this.updateLauncherBottomCss(), this.updateMetaInfo(), this.loadServerOverride();
    for (const t of this.draft.images)
      t.url || (t.url = URL.createObjectURL(t.blob));
    this.visibilityHandler || (this.visibilityHandler = () => {
      document.visibilityState === "visible" ? (this.quotaRefreshFailed = !1, this.refreshQuota(), this.pollSummary(), this.openState && this.activeTab === "history" && this.activeDialogueId && this.loadDialogue(this.activeDialogueId, !0)) : (this.invalidateQuotaRefresh(), this.quotaRefreshPending = !1);
    }, document.addEventListener("visibilitychange", this.visibilityHandler)), this.tokenValid() && this.startSummaryPolling(), this.openState && this._logProvider && !this.draft.logsCollected && this.draft.logs.length === 0 && !this.logsCollecting && this.phase !== "submitting" && this.collectLogs(), this.sessionStore && !this.tokenValid() && this.loadHostSession(), this.syncUi();
  }
  disconnectedCallback() {
    this.stopDialoguePolling(), this.stopSummaryPolling(), this.invalidateLogCollection(), this.invalidateCaptureSession(), this.captureInFlight = null, this.stopPolling(), this.invalidateLogin(), this.invalidateQuotaRefresh(), this.quotaRefreshPending = !1, this.visibilityHandler && (document.removeEventListener("visibilitychange", this.visibilityHandler), this.visibilityHandler = null), this.loginPassword.value = "", this.keydownHandler && (document.removeEventListener("keydown", this.keydownHandler, !0), this.keydownHandler = null);
    for (const t of this.draft.images)
      t.url && (URL.revokeObjectURL(t.url), t.url = "");
    this.editorHandle?.close(), this.editorHandle = null, this.closeZoomModal();
  }
  attributeChangedCallback(t, e, s) {
    if (t === "api-base" && e !== s && (this.settingsOpen = !1, this.settingsConfirmOpen = !1, this.pendingServerTarget = void 0, this.loadServerOverride(), this.identityEpoch++, this.quotaRefreshPending = !1, this.resetForServiceSwitch()), t === "app-id" && e !== s) {
      const n = this.effectiveApiBase;
      this.settingsOpen = !1, this.settingsConfirmOpen = !1, this.pendingServerTarget = void 0, this.loadServerOverride();
      const r = this.effectiveApiBase;
      this.identityEpoch++, this.quotaRefreshPending = !1, this.sameNormalizedBase(n, r) ? this.resetForAppIdSwitch() : this.resetForServiceSwitch();
    }
    t === "launcher-bottom" && this.updateLauncherBottomCss(), (t === "app-version" || t === "page-label") && this.updateMetaInfo();
  }
  /**
   * `api-base` 变化 = 完整身份切换：取消并清空一切属于旧服务的东西
   * （捕获会话 / 提交快照 / 幂等键 / 握手 / 轮询 / 令牌 / 任务态 / 草稿）。
   * 新服务必须重新登录，旧令牌绝不外泄给新基址；旧服务的迟到响应由 epoch 校验丢弃。
   */
  resetForServiceSwitch() {
    this.invalidateLogCollection(), this.invalidateCaptureSession(), this.invalidateLogin(), this.invalidateQuotaRefresh(), this.captureInFlight = null, this.stopPolling(), this.pollDelay = yt, this.pollStartedAt = 0, this.submitSnapshot = null, this.idempotencyKey = null, this.unconfirmedRequest = null, this.accessToken = null, this.tokenExpiresAt = 0, this.authRequired = !1, this.pendingSubmit = !1, this.authUser = null, this.quota = null, this.loginVisible = !1, this.loginBusy = !1, this.loginError = "", this.loginPassword.value = "", this.lastFeedbackId = null, this.lastRecord = null, this.lastErrorSummary = null, this.lastSubmittedText = "", this.refreshing = !1, this.phase = "idle", this.capabilitiesKnown = !1, this.maxImages = J, this._diagnostics?.clear(), this.editorHandle?.close(), this.editorHandle = null, this.clearDraft(), this.textarea.value = "", this.renderStatus(""), this.syncUi();
  }
  /**
   * `app-id` 变化：同一服务内的身份切换——令牌保留（同一服务），
   * 但旧身份的草稿 / 捕获 / 提交结果 / 轮询 / 握手全部作废。
   */
  resetForAppIdSwitch() {
    this.invalidateLogCollection(), this.invalidateCaptureSession(), this.invalidateLogin(), this.invalidateQuotaRefresh(), this.captureInFlight = null, this.restoreCaptureUi(), this.stopPolling(), this.pollDelay = yt, this.pollStartedAt = 0, this.capabilitiesKnown = !1, this.maxImages = J, this._diagnostics?.clear(), this.editorHandle?.close(), this.editorHandle = null, this.clearDraft(), this.textarea.value = "", this.submitSnapshot = null, this.idempotencyKey = null, this.unconfirmedRequest = null, this.lastFeedbackId = null, this.lastRecord = null, this.lastErrorSummary = null, this.lastSubmittedText = "", this.refreshing = !1, this.phase = "idle", this.loginVisible = !1, this.loginBusy = !1, this.loginError = "", this.loginPassword.value = "", this.pendingSubmit = !1, this.renderStatus(""), this.syncUi();
  }
  updateLauncherBottomCss() {
    const t = this.launcherBottom;
    t && this.style.setProperty("--fb-launcher-bottom", t);
  }
  updateMetaInfo() {
    if (!this.metaInfo) return;
    const t = [];
    this.appVersion && t.push(`v${this.appVersion}`), this.pageLabel && t.push(this.pageLabel), this.metaInfo.textContent = t.length > 0 ? t.join(" · ") : "", this.metaInfo.hidden = t.length === 0;
  }
  initOrbGesture() {
    let t = 0, e = 0, s = !1;
    this.orb.addEventListener("pointerdown", (r) => {
      if (r.button === 0) {
        t = r.clientX, e = r.clientY, s = !0, this.isDragging = !1, this.orbPointerId = r.pointerId;
        try {
          this.orb.setPointerCapture(r.pointerId);
        } catch {
        }
      }
    }), this.orb.addEventListener("pointermove", (r) => {
      if (!s || this.orbPointerId !== r.pointerId) return;
      const a = r.clientX - t, o = r.clientY - e;
      if (this.isDragging || Math.hypot(a, o) > 8 && (this.isDragging = !0, this.orb.classList.add("is-dragging")), this.isDragging) {
        const h = this.prefersReducedMotion() ? 1 : 1.5;
        this.orb.style.transform = `translate3d(${a}px, ${o}px, 0) scale(${h})`;
      }
    });
    const n = (r, a = !1) => {
      if (!s || this.orbPointerId !== r.pointerId) return;
      s = !1;
      try {
        this.orb.releasePointerCapture(r.pointerId);
      } catch {
      }
      this.orbPointerId = null;
      const o = this.isDragging;
      if (this.isDragging = !1, this.orb.classList.remove("is-dragging"), this.orb.style.transform = "", !a)
        if (o) {
          const h = typeof window < "u" && window.innerWidth || 1, d = typeof window < "u" && window.innerHeight || 1, c = Math.max(0, Math.min(1, r.clientX / h)), u = Math.max(0, Math.min(1, r.clientY / d)), f = {
            x: Math.round(c * 1e4) / 1e4,
            y: Math.round(u * 1e4) / 1e4
          };
          this.startRegionCapture({ releasePoint: f });
        } else
          this.captureMode === "viewport" ? this.captureAndOpen() : this.open();
    };
    this.orb.addEventListener("pointerup", (r) => n(r, !1)), this.orb.addEventListener("pointercancel", (r) => n(r, !0)), this.orb.addEventListener("keydown", (r) => {
      (r.key === " " || r.key === "Enter") && (r.preventDefault(), this.captureMode === "viewport" ? this.captureAndOpen() : this.open());
    });
  }
  // ---------- DOM 构建 ----------
  buildDom() {
    const t = l("style");
    t.textContent = Ii, this.launcher = l("button", "fb-launcher fb-fab fb-tab-launcher"), this.launcher.type = "button", this.launcher.setAttribute("aria-label", "打开反馈面板"), this.launcher.setAttribute("aria-expanded", "false"), this.launcher.setAttribute("aria-controls", "fb-panel");
    const e = ge("fb-launcher-icon"), s = l("span", void 0, "反馈");
    this.launcherBadge = l("span", "fb-badge fb-launcher-badge"), this.launcherBadge.hidden = !0, this.launcher.append(e, s, this.launcherBadge), this.orb = l("button", "fb-launcher fb-orb"), this.orb.type = "button", this.orb.setAttribute("aria-label", "灵感球：拖动指出问题或点击反馈"), this.orb.setAttribute("aria-expanded", "false"), this.orb.setAttribute("aria-controls", "fb-panel"), this.orbBadge = l("span", "fb-badge fb-launcher-badge"), this.orbBadge.hidden = !0;
    const n = Ni();
    this.orb.append(n.glow, n.core, this.orbBadge), this.panel = l("div", "fb-panel"), this.panel.id = "fb-panel", this.panel.setAttribute("role", "dialog"), this.panel.setAttribute("aria-label", "记录体验"), this.panel.setAttribute("aria-modal", "false"), this.panel.hidden = !0;
    const r = l("header", "fb-header"), a = l("div", "fb-title-group"), o = ge("fb-header-icon"), h = l("h2", void 0, "记录体验");
    this.metaInfo = l("span", "fb-header-meta"), this.metaInfo.hidden = !0, a.append(o, h, this.metaInfo);
    const d = l("div", "fb-header-actions");
    this.settingsBtn = l("button", "fb-icon-btn fb-settings-btn"), this.settingsBtn.type = "button", this.settingsBtn.setAttribute("aria-label", "服务器设置"), this.settingsBtn.title = "服务器设置", this.settingsBtn.append($i()), this.settingsBtn.addEventListener("click", () => this.openServerSettings());
    const c = l("button", "fb-close");
    c.type = "button", c.setAttribute("aria-label", "关闭反馈面板"), c.append(qt()), c.addEventListener("click", () => this.close()), d.append(this.settingsBtn, c), r.append(a, d);
    const u = l("div", "fb-nav-tabs");
    u.style.padding = "0 16px 8px", u.style.borderBottom = "1px solid var(--fb-border)", this.tabComposeBtn = l("button", "fb-nav-btn active", "新建反馈"), this.tabComposeBtn.type = "button", this.tabComposeBtn.addEventListener("click", () => this.switchTab("compose")), this.tabHistoryBtn = l("button", "fb-nav-btn"), this.tabHistoryBtn.type = "button";
    const f = l("span", void 0, "我的反馈");
    this.tabHistoryBadge = l("span", "fb-badge"), this.tabHistoryBadge.hidden = !0, this.tabHistoryBadge.style.marginLeft = "4px", this.tabHistoryBtn.append(f, this.tabHistoryBadge), this.tabHistoryBtn.addEventListener("click", () => this.switchTab("history")), u.append(this.tabComposeBtn, this.tabHistoryBtn), this.navTabs = u;
    const x = l("div", "fb-body");
    this.body = x, this.shotArea = l("div", "fb-shot-area"), this.screenshotWrap = l("div", "fb-screenshot-wrap"), this.screenshotWrap.hidden = !0, this.screenshotThumbBox = l("div", "fb-screenshot-thumb-box"), this.screenshotThumbBox.setAttribute("role", "button"), this.screenshotThumbBox.setAttribute("tabindex", "0"), this.screenshotThumbBox.setAttribute("aria-label", "查看图片 1");
    const b = l("div", "fb-thumb-frame");
    this.screenshotThumb = l("img", "fb-screenshot-thumb"), this.screenshotThumb.alt = "图片缩略图";
    const m = l("span", "fb-screenshot-badge", "截图"), A = l("span", "fb-screenshot-size"), v = l("span", "fb-screenshot-zoom-hint", "点击放大");
    b.append(this.screenshotThumb, m, A, v);
    const T = l("div", "fb-thumb-actions"), B = l("button", "fb-thumb-edit", "编辑");
    B.type = "button", B.setAttribute("aria-label", "编辑图片 1"), B.addEventListener("click", (E) => {
      E.stopPropagation();
      const $ = this.draft.images[0];
      $ && this.openEditorForImage($.id);
    });
    const k = l("button", "fb-thumb-remove", "删除");
    k.type = "button", k.setAttribute("aria-label", "删除图片 1"), k.addEventListener("click", (E) => {
      E.stopPropagation();
      const $ = this.draft.images[0];
      $ && this.removeImage($.id);
    }), T.append(B, k), this.screenshotThumbBox.append(b, T), this.screenshotWrap.append(this.screenshotThumbBox), this.shotHintEl = l("p", "fb-shot-hint"), this.shotHintEl.hidden = !0, this.shotErrorEl = l("p", "fb-shot-error"), this.shotErrorEl.hidden = !0, this.shotErrorEl.setAttribute("role", "alert");
    const S = l("div", "fb-screenshot-actions");
    this.captureBtn = l("button", "fb-btn-capture", he), this.captureBtn.type = "button", this.captureBtn.setAttribute("aria-label", "截取当前页面并附加截图"), this.retakeBtn = l("button", "fb-btn-retake", "重新截图"), this.retakeBtn.type = "button", this.retakeBtn.setAttribute("aria-label", "重新捕获屏幕截图"), this.retakeBtn.hidden = !0, this.removeBtn = l("button", "fb-btn-remove", "移除截图"), this.removeBtn.type = "button", this.removeBtn.setAttribute("aria-label", "移除当前截图"), this.removeBtn.hidden = !0, this.addImageBtn = l("button", "fb-btn-add-image", "添加图片"), this.addImageBtn.type = "button", this.addImageBtn.setAttribute("aria-label", "添加图片附件（选择文件，也可拖入或粘贴）"), this.imageFileInput = l("input", "fb-image-input"), this.imageFileInput.type = "file", this.imageFileInput.accept = "image/png,image/jpeg,image/webp,.png,.jpg,.jpeg,.webp", this.imageFileInput.multiple = !0, this.imageFileInput.hidden = !0, this.imageFileInput.setAttribute("aria-hidden", "true"), S.append(this.captureBtn, this.retakeBtn, this.removeBtn, this.addImageBtn), this.shotArea.append(this.screenshotWrap, this.shotErrorEl, this.shotHintEl, S, this.imageFileInput), this.captureBtn.addEventListener("click", () => this.captureFromPanel()), this.addImageBtn.addEventListener("click", () => this.imageFileInput.click()), this.imageFileInput.addEventListener("change", () => {
      const E = Array.from(this.imageFileInput.files ?? []);
      this.imageFileInput.value = "", this.addImageFiles(E);
    }), this.screenshotThumbBox.addEventListener("click", () => this.openZoomModal()), this.screenshotThumbBox.addEventListener("keydown", (E) => {
      (E.key === " " || E.key === "Enter") && (E.preventDefault(), this.openZoomModal());
    }), this.retakeBtn.addEventListener("click", () => {
      this.retakeScreenshot();
    }), this.removeBtn.addEventListener("click", () => this.removeScreenshot());
    const I = l("label", "fb-prompt", "哪里不顺手，或者有什么新想法？");
    I.setAttribute("for", "fb-textarea");
    const M = l("div", "fb-textarea-wrap");
    this.textarea = l("textarea", "fb-textarea"), this.textarea.id = "fb-textarea", this.textarea.placeholder = "刚才哪里不顺手？你希望它怎样改进？", this.textarea.setAttribute("aria-label", "反馈内容"), this.textarea.value = this.draft.text;
    const P = l("div", "fb-counter-row");
    this.counter = l("span", "fb-counter"), P.append(this.counter), M.append(this.textarea, P), this.statusRegion = l("div", "fb-status fb-status-region"), this.statusRegion.setAttribute("role", "status"), this.statusRegion.setAttribute("aria-live", "polite"), this.errorRegion = l("div", "fb-error fb-status-card-wrap"), this.errorRegion.hidden = !0;
    const U = l("footer", "fb-footer");
    this.quotaInfo = l("span", "fb-quota"), this.quotaInfo.hidden = !0, this.quotaInfo.setAttribute("role", "status"), this.quotaBlockedInfo = l("p", "fb-quota-blocked"), this.quotaBlockedInfo.hidden = !0, this.loginPanel = l("div", "fb-login-panel"), this.loginPanel.hidden = !0, this.loginPanel.setAttribute("role", "group"), this.loginPanel.setAttribute("aria-label", "账号登录"), this.loginUsername = l("input", "fb-login-input fb-login-username"), this.loginUsername.type = "text", this.loginUsername.autocomplete = "username", this.loginUsername.placeholder = "用户名", this.loginUsername.setAttribute("aria-label", "用户名"), this.loginPassword = l("input", "fb-login-input fb-login-password"), this.loginPassword.type = "password", this.loginPassword.autocomplete = "current-password", this.loginPassword.placeholder = "密码", this.loginPassword.setAttribute("aria-label", "密码"), this.loginErrorEl = l("p", "fb-login-error"), this.loginErrorEl.hidden = !0, this.loginConfirmBtn = l("button", "fb-btn-secondary fb-login-confirm", "登录并提交"), this.loginConfirmBtn.type = "button", this.loginConfirmBtn.setAttribute("aria-label", "登录并提交反馈"), this.loginCancelBtn = l("button", "fb-btn-secondary fb-login-cancel", "取消"), this.loginCancelBtn.type = "button";
    const q = l("div", "fb-login-actions");
    q.append(this.loginConfirmBtn, this.loginCancelBtn);
    const _ = l("div", "fb-login-row");
    _.append(this.loginUsername, this.loginPassword), this.loginPanel.append(_, this.loginErrorEl, q), this.submitBtn = l("button", "fb-submit", "提交"), this.submitBtn.type = "button", this.submitBtn.setAttribute("aria-label", "提交反馈");
    const C = l("p", "fb-footnote", "会保留原话，整理为候选改进");
    U.append(this.quotaInfo, this.quotaBlockedInfo, this.loginPanel, this.submitBtn, C), this.loginConfirmBtn.addEventListener("click", () => {
      this.doLogin();
    }), this.loginCancelBtn.addEventListener("click", () => this.cancelLogin()), this.loginPassword.addEventListener("keydown", (E) => {
      E.key === "Enter" && (E.preventDefault(), this.doLogin());
    }), this.logsArea = l("div", "fb-logs-area"), this.logsToggleBtn = l("button", "fb-logs-toggle"), this.logsToggleBtn.type = "button", this.logsToggleBtn.setAttribute("aria-expanded", "false"), this.logsToggleBtn.setAttribute("aria-controls", "fb-logs-body"), this.logsSummaryEl = l("span", "fb-logs-summary", "诊断日志");
    const K = l("span", "fb-logs-chevron", "▸");
    K.setAttribute("aria-hidden", "true"), this.logsToggleBtn.append(this.logsSummaryEl, K), this.logsToggleBtn.addEventListener("click", () => this.toggleLogs()), this.logsBody = l("div", "fb-logs-body"), this.logsBody.id = "fb-logs-body", this.logsBody.hidden = !0;
    const D = l("div", "fb-logs-header"), j = l("span", void 0, "日志附件");
    this.logsCountEl = l("span", "fb-logs-count", " (0/3)"), j.append(this.logsCountEl);
    const N = l("div", "fb-logs-actions");
    this.addLogBtn = l("button", "fb-btn-add-log", "添加日志"), this.addLogBtn.type = "button", this.addLogBtn.setAttribute("aria-label", "手动添加日志文件"), this.recollectLogBtn = l("button", "fb-btn-add-log fb-log-recollect", "重新采集"), this.recollectLogBtn.type = "button", this.recollectLogBtn.setAttribute("aria-label", "重新采集诊断日志"), this.recollectLogBtn.hidden = !0, this.logFileInput = l("input", "fb-log-input"), this.logFileInput.type = "file", this.logFileInput.accept = ".log,.txt,.json,.jsonl", this.logFileInput.multiple = !0, this.logFileInput.hidden = !0, this.addLogBtn.addEventListener("click", () => {
      this.logFileInput.click();
    }), this.recollectLogBtn.addEventListener("click", () => {
      this.phase !== "submitting" && this.collectLogs();
    }), this.logFileInput.addEventListener("change", () => {
      this.handleLogFileSelect();
    }), N.append(this.addLogBtn, this.recollectLogBtn, this.logFileInput), D.append(j, N), this.logStatusEl = l("span", "fb-log-status"), this.logStatusEl.hidden = !0, this.logErrorEl = l("div", "fb-log-error"), this.logErrorEl.hidden = !0, this.logRetryBtn = l("button", "fb-btn-secondary fb-log-retry", "重试"), this.logRetryBtn.type = "button", this.logRetryBtn.setAttribute("aria-label", "重试采集日志"), this.logRetryBtn.addEventListener("click", () => {
      this.phase !== "submitting" && this.collectLogs();
    }), this.logsList = l("ul", "fb-logs-list"), this.logsBody.append(D, this.logStatusEl, this.logErrorEl, this.logsList), this.logsArea.append(this.logsToggleBtn, this.logsBody), x.append(
      I,
      M,
      this.shotArea,
      this.logsArea,
      this.statusRegion,
      this.errorRegion,
      U
    ), this.panel.append(r, this.navTabs, x, this.buildHistoryView(), this.buildSettingsView()), this.zoomModal = l("div", "fb-zoom-modal"), this.zoomModal.setAttribute("role", "dialog"), this.zoomModal.setAttribute("aria-label", "完整截图预览"), this.zoomImg = l("img"), this.zoomImg.alt = "完整截图预览", this.zoomCloseBtn = l("button", "fb-zoom-close"), this.zoomCloseBtn.type = "button", this.zoomCloseBtn.setAttribute("aria-label", "关闭截图预览"), this.zoomCloseBtn.append(qt()), this.zoomModal.append(this.zoomImg, this.zoomCloseBtn), this.zoomModal.addEventListener("click", (E) => {
      (E.target === this.zoomModal || E.target === this.zoomCloseBtn || this.zoomCloseBtn.contains(E.target)) && this.closeZoomModal();
    }), this.regionOverlay = l("div", "fb-region-overlay"), this.regionOverlay.setAttribute("role", "dialog"), this.regionOverlay.setAttribute("aria-modal", "true"), this.regionOverlay.setAttribute("aria-label", "选择截图区域"), this.regionOverlay.hidden = !0, this.regionBg = l("img", "fb-region-bg"), this.regionBg.alt = "", this.regionBg.setAttribute("aria-hidden", "true"), this.regionFrame = l("div", "fb-region-frame"), this.regionFrame.tabIndex = 0, this.regionFrame.setAttribute("role", "group"), this.regionFrame.setAttribute(
      "aria-label",
      "截图选区：方向键移动，Shift+方向键缩放，回车确认使用此区域，Esc 取消"
    );
    for (const E of se) {
      const $ = l("span", "fb-region-handle");
      $.setAttribute("data-handle", E), $.setAttribute("aria-hidden", "true"), this.regionFrame.append($);
    }
    const W = l("div", "fb-region-toolbar");
    this.regionSizeEl = l("span", "fb-region-size"), this.regionUseBtn = l("button", "fb-region-btn fb-region-use", "使用此区域"), this.regionAllBtn = l("button", "fb-region-btn fb-region-all", "截取整个窗口"), this.regionCancelBtn = l("button", "fb-region-btn fb-region-cancel", "取消");
    for (const E of [this.regionUseBtn, this.regionAllBtn, this.regionCancelBtn]) E.type = "button";
    this.regionUseBtn.setAttribute("aria-label", "使用当前选区作为反馈截图"), this.regionAllBtn.setAttribute("aria-label", "使用整张画面作为反馈截图"), this.regionCancelBtn.setAttribute("aria-label", "取消局部截图"), W.append(this.regionSizeEl, this.regionUseBtn, this.regionAllBtn, this.regionCancelBtn), this.regionErrorEl = l("p", "fb-region-error"), this.regionErrorEl.hidden = !0, this.regionErrorEl.setAttribute("role", "alert"), this.regionOverlay.append(this.regionBg, this.regionFrame, W, this.regionErrorEl), this.regionUseBtn.addEventListener("click", () => {
      this.confirmRegion(!0);
    }), this.regionAllBtn.addEventListener("click", () => {
      this.confirmRegion(!1);
    }), this.regionCancelBtn.addEventListener("click", () => this.cancelRegion()), this.regionOverlay.addEventListener("pointerdown", (E) => this.onRegionPointerDown(E)), this.regionOverlay.addEventListener("pointermove", (E) => this.onRegionPointerMove(E)), this.regionOverlay.addEventListener("pointerup", (E) => this.onRegionPointerEnd(E)), this.regionOverlay.addEventListener("pointercancel", (E) => this.onRegionPointerEnd(E)), this.logPreviewModal = l("div", "fb-log-preview-modal"), this.logPreviewModal.setAttribute("role", "dialog"), this.logPreviewModal.setAttribute("aria-label", "日志预览");
    const X = l("div", "fb-log-preview-card"), it = l("div", "fb-header");
    this.logPreviewTitle = l("h3", void 0, "日志预览"), this.logPreviewCloseBtn = l("button", "fb-close"), this.logPreviewCloseBtn.type = "button", this.logPreviewCloseBtn.setAttribute("aria-label", "关闭日志预览"), this.logPreviewCloseBtn.append(qt()), this.logPreviewCloseBtn.addEventListener("click", () => this.closeLogPreview()), it.append(this.logPreviewTitle, this.logPreviewCloseBtn), this.logPreviewBody = l("pre", "fb-log-preview-body"), X.append(it, this.logPreviewBody), this.logPreviewModal.append(X), this.logPreviewModal.addEventListener("click", (E) => {
      E.target === this.logPreviewModal && this.closeLogPreview();
    }), this.root.append(
      t,
      this.launcher,
      this.orb,
      this.panel,
      this.zoomModal,
      this.logPreviewModal,
      this.regionOverlay
    ), this.addEventListener("keydown", (E) => this.onKeydown(E));
  }
  /**
   * T6：服务器设置视图（面板内、与主体互斥显示；未登录也可进入）。
   * 提供地址输入、保存、取消、恢复默认与当前有效地址展示；
   * 地址变化且有草稿 / 未确认提交时先展开内联确认块。
   */
  buildSettingsView() {
    this.settingsView = l("div", "fb-settings"), this.settingsView.hidden = !0, this.settingsView.setAttribute("role", "group"), this.settingsView.setAttribute("aria-label", "服务器设置");
    const t = l("h3", "fb-settings-title", "Feedback 服务器"), e = l("p", "fb-settings-line");
    e.append(l("span", "fb-settings-label", "当前使用：")), this.settingsCurrent = l("span", "fb-settings-value"), e.append(this.settingsCurrent), this.settingsDefaultRow = l("p", "fb-settings-line"), this.settingsDefaultRow.hidden = !0, this.settingsDefaultRow.append(l("span", "fb-settings-label", "宿主默认：")), this.settingsDefaultText = l("span", "fb-settings-value"), this.settingsDefaultRow.append(this.settingsDefaultText);
    const s = l("label", "fb-prompt", "服务器地址");
    s.setAttribute("for", "fb-server-input"), this.settingsInput = l("input", "fb-login-input fb-server-input"), this.settingsInput.id = "fb-server-input", this.settingsInput.type = "url", this.settingsInput.placeholder = "https://fb.example.com", this.settingsInput.autocomplete = "off", this.settingsInput.spellcheck = !1, this.settingsInput.setAttribute("aria-label", "服务器地址"), this.settingsInput.addEventListener("keydown", (d) => {
      d.key === "Enter" && !d.isComposing && d.keyCode !== 229 && (d.preventDefault(), this.saveServerSettings());
    }), this.settingsErrorEl = l("p", "fb-settings-error"), this.settingsErrorEl.hidden = !0, this.settingsHintEl = l("p", "fb-settings-hint"), this.settingsHintEl.hidden = !0, this.settingsHintEl.setAttribute("role", "status");
    const n = l("div", "fb-settings-actions");
    this.settingsSaveBtn = l("button", "fb-btn-secondary fb-settings-save", "保存"), this.settingsSaveBtn.type = "button", this.settingsRestoreBtn = l("button", "fb-btn-secondary fb-settings-restore", "恢复默认"), this.settingsRestoreBtn.type = "button", this.settingsCancelBtn = l("button", "fb-btn-secondary fb-settings-cancel", "返回"), this.settingsCancelBtn.type = "button", n.append(this.settingsSaveBtn, this.settingsRestoreBtn, this.settingsCancelBtn), this.settingsConfirmEl = l("div", "fb-settings-confirm"), this.settingsConfirmEl.hidden = !0, this.settingsConfirmText = l("p", "fb-settings-confirm-text");
    const r = l("div", "fb-settings-actions"), a = l("button", "fb-btn-secondary fb-settings-confirm-ok", "清空并切换");
    a.type = "button";
    const o = l("button", "fb-btn-secondary fb-settings-confirm-cancel", "取消");
    o.type = "button", r.append(a, o), this.settingsConfirmEl.append(this.settingsConfirmText, r);
    const h = l("p", "fb-footnote", "支持 http(s) 地址，可包含端口与部署路径前缀；仅本机记住选择。");
    return this.settingsView.append(
      t,
      e,
      this.settingsDefaultRow,
      s,
      this.settingsInput,
      this.settingsErrorEl,
      this.settingsHintEl,
      n,
      this.settingsConfirmEl,
      h
    ), this.settingsSaveBtn.addEventListener("click", () => this.saveServerSettings()), this.settingsRestoreBtn.addEventListener("click", () => this.requestRestoreDefault()), this.settingsCancelBtn.addEventListener("click", () => this.closeServerSettings()), a.addEventListener("click", () => this.confirmServerSwitch()), o.addEventListener("click", () => this.cancelServerSwitch()), this.settingsView;
  }
  // ---------- 历史反馈与 Issue 对话 ----------
  buildHistoryView() {
    return this.historyContainer = l("div", "fb-history-container"), this.historyContainer.style.display = "flex", this.historyContainer.style.flexDirection = "column", this.historyContainer.style.flex = "1", this.historyContainer.style.overflow = "hidden", this.historyContainer.style.padding = "0 16px", this.historyContainer.hidden = !0, this.historyListView = l("div", "fb-history-view"), this.dialogueView = l("div", "fb-dialogue-view"), this.dialogueView.hidden = !0, this.historyContainer.append(this.historyListView, this.dialogueView), this.historyContainer;
  }
  switchTab(t) {
    this.activeTab = t, t === "compose" && (this.activeDialogueId = null, this.stopDialoguePolling(), this.phase !== "submitting" && this.phase !== "tracking" && this.startSummaryPolling()), this.syncUi(), t === "history" && (this.activeDialogueId ? (this.stopSummaryPolling(), this.startDialoguePolling(), this.loadDialogue(this.activeDialogueId)) : (this.startSummaryPolling(), this.loadHistoryList()));
  }
  syncUnreadBadge() {
    const t = this.unreadCount > 0, e = this.unreadCount > 99 ? "99+" : String(this.unreadCount);
    this.launcherBadge && (this.launcherBadge.hidden = !t, this.launcherBadge.textContent = e), this.orbBadge && (this.orbBadge.hidden = !t, this.orbBadge.textContent = e), this.tabHistoryBadge && (this.tabHistoryBadge.hidden = !t, this.tabHistoryBadge.textContent = e);
  }
  /**
   * 未读摘要轮询（v12 口径）：**不再以面板打开为前提**——已登录且页面可见时
   * 每 30 秒查一次 unread-summary（面板关闭也要给入口徽标供数）。
   * 面板销毁 / 页面隐藏 / 退出登录时停止；提交跟踪进行中时跳过（提交轮询优先）。
   */
  startSummaryPolling() {
    this.stopSummaryPolling(), !(!this.tokenValid() || !this.isConnected) && (this.activeTab === "compose" && (this.phase === "submitting" || this.phase === "tracking") || (this.summaryPollTimer = setInterval(() => {
      document.visibilityState === "visible" && this.tokenValid() && !(this.activeTab === "compose" && (this.phase === "submitting" || this.phase === "tracking")) && this.pollSummary();
    }, 3e4)));
  }
  stopSummaryPolling() {
    this.summaryPollTimer && (clearInterval(this.summaryPollTimer), this.summaryPollTimer = null);
  }
  async pollSummary() {
    if (!(!this.tokenValid() || !this.effectiveApiBase))
      try {
        const t = await Ue(this.effectiveApiBase, this.accessToken, this.appId ?? void 0, { fetchFn: this.apiFetch });
        this.unreadCount = t.totalUnreadMessages, this.syncUnreadBadge();
      } catch {
      }
  }
  startDialoguePolling() {
    this.stopDialoguePolling(), !(!this.tokenValid() || !this.openState) && (this.dialoguePollTimer = setInterval(() => {
      document.visibilityState === "visible" && this.activeDialogueId && this.tokenValid() && this.openState && this.loadDialogue(this.activeDialogueId, !0);
    }, 1e4));
  }
  stopDialoguePolling() {
    this.dialoguePollTimer && (clearInterval(this.dialoguePollTimer), this.dialoguePollTimer = null);
  }
  async loadHistoryList() {
    if (!this.tokenValid() || !this.effectiveApiBase) {
      this.historyListView.innerHTML = "";
      const o = l("div", "fb-history-empty");
      o.style.textAlign = "center", o.style.padding = "24px 0";
      const h = l("p", "fb-muted", "查看反馈历史需要登录"), d = l("button", "fb-btn-secondary", "去登录");
      d.type = "button", d.style.marginTop = "8px", d.addEventListener("click", () => {
        this.switchTab("compose"), this.beginLogin(!1);
      }), o.append(h, d), this.historyListView.append(o);
      return;
    }
    this.historyListView.innerHTML = "";
    const t = l("div", "fb-history-filter-row"), e = l("select", "fb-history-filter");
    e.setAttribute("aria-label", "按问题状态筛选");
    const s = [
      ["", "全部状态"],
      ["open", "待处理"],
      ["waiting_user", "待补充"],
      ["waiting_admin", "待管理员回复"],
      ["resolved", "已解决"]
    ];
    for (const [o, h] of s) {
      const d = l("option");
      d.value = o, d.textContent = h, e.append(d);
    }
    e.value = this.historyIssueStatus, e.addEventListener("change", () => {
      this.historyIssueStatus = e.value || "", this.historyPage = 1, this.loadHistoryList();
    }), t.append(e);
    const n = l("div", "fb-history-items"), r = l("div", void 0, "加载反馈列表中…");
    r.style.padding = "16px", r.style.textAlign = "center", r.style.color = "var(--fb-text-secondary)", n.append(r);
    const a = l("div", "fb-history-pager");
    this.historyListView.append(t, n, a);
    try {
      const o = await Oe(this.effectiveApiBase, this.accessToken, {
        appId: this.appId ?? void 0,
        page: this.historyPage,
        limit: 20,
        ...this.historyIssueStatus ? { issueStatus: this.historyIssueStatus } : {},
        fetchFn: this.apiFetch
      });
      n.innerHTML = "", a.innerHTML = "";
      const h = o.limit > 0 ? o.limit : 20, d = Math.max(1, Math.ceil(o.total / h));
      if (o.total > 0 && this.historyPage > d) {
        this.historyPage = d, this.loadHistoryList();
        return;
      }
      if (o.items.length === 0) {
        const c = l(
          "div",
          void 0,
          this.historyIssueStatus ? "该状态下暂无反馈记录" : "暂无反馈记录"
        );
        c.style.padding = "24px", c.style.textAlign = "center", c.style.color = "var(--fb-text-secondary)", n.append(c);
      }
      for (const c of o.items) {
        const u = l("div", "fb-history-item");
        u.setAttribute("role", "button"), u.setAttribute("tabindex", "0");
        const f = l("div", "fb-history-item-header"), x = l("span", "fb-history-title", c.title || c.text.slice(0, 30) || "（无标题）"), b = l("div", "fb-history-tags"), m = l(
          "span",
          `fb-tag ${c.issueStatus === "waiting_admin" ? "waiting-admin" : c.issueStatus === "waiting_user" ? "waiting-user" : c.issueStatus === "resolved" ? "resolved" : ""}`,
          Tt[c.issueStatus] ?? c.issueStatus
        );
        if (b.append(m), c.unread) {
          const B = l("span", "fb-badge", "未读");
          b.append(B);
        }
        f.append(x, b);
        const A = c.latestMessage ? `${c.latestMessage.senderType === "admin" ? "管理员" : "用户"}: ${c.latestMessage.text}` : c.text, v = l("div", "fb-history-preview", A);
        v.style.fontSize = "12px", v.style.color = "var(--fb-text-secondary)", v.style.margin = "4px 0", v.style.overflow = "hidden", v.style.textOverflow = "ellipsis", v.style.whiteSpace = "nowrap";
        const T = l("div", "fb-history-date", new Date(c.createdAt).toLocaleString());
        T.style.fontSize = "11px", T.style.color = "var(--fb-text-tertiary)", u.append(f, v, T), u.addEventListener("click", () => {
          this.openDialogue(c.id);
        }), u.addEventListener("keydown", (B) => {
          (B.key === "Enter" || B.key === " ") && (B.preventDefault(), this.openDialogue(c.id));
        }), n.append(u);
      }
      if (o.total > h) {
        const c = l("button", "fb-btn-secondary fb-history-page-prev", "上一页");
        c.type = "button", c.disabled = this.historyPage <= 1, c.addEventListener("click", () => {
          this.historyPage > 1 && (this.historyPage--, this.loadHistoryList());
        });
        const u = l("button", "fb-btn-secondary fb-history-page-next", "下一页");
        u.type = "button", u.disabled = this.historyPage >= d, u.addEventListener("click", () => {
          this.historyPage < d && (this.historyPage++, this.loadHistoryList());
        });
        const f = l("span", "fb-history-page-info", `第 ${this.historyPage} / ${d} 页`);
        a.append(c, f, u);
      }
    } catch (o) {
      n.innerHTML = "";
      const h = l("div", "fb-error");
      h.style.padding = "16px", h.style.textAlign = "center", h.textContent = o instanceof Error ? o.message : "加载失败";
      const d = l("button", "fb-btn-secondary", "重试");
      d.type = "button", d.style.marginTop = "8px", d.addEventListener("click", () => {
        this.loadHistoryList();
      }), h.append(document.createElement("br"), d), n.append(h);
    }
  }
  async openDialogue(t) {
    this.activeDialogueId = t, this.dialogueRenderedSeq = 0, this.revokeAttachmentUrls(), this.historyListView.hidden = !0, this.dialogueView.hidden = !1, this.startDialoguePolling(), await this.loadDialogue(t);
  }
  closeDialogue() {
    this.activeDialogueId = null, this.dialogueRenderedSeq = 0, this.revokeAttachmentUrls(), this.clearReplyImages(), this.stopDialoguePolling(), this.startSummaryPolling(), this.dialogueView.hidden = !0, this.historyListView.hidden = !1, this.loadHistoryList();
  }
  /** 回收本次对话用过的 Bearer 附件对象 URL。 */
  revokeAttachmentUrls() {
    for (const t of this.attachmentUrls) URL.revokeObjectURL(t);
    this.attachmentUrls.clear();
  }
  /** Bearer 取回附件并转对象 URL（v12：历史图片/日志绝不以裸 URL 渲染）。 */
  async attachmentUrl(t) {
    const e = await Kt(this.effectiveApiBase, this.accessToken, t, { fetchFn: this.apiFetch }), s = URL.createObjectURL(e);
    return this.attachmentUrls.add(s), s;
  }
  /** 以 Bearer 方式取回附件并渲染进 <img>（失败降级为文件名文本，不阻断消息）。 */
  renderAttachmentImage(t, e, s) {
    const n = l("img", "fb-dialogue-attachment-img");
    n.alt = s, n.style.maxHeight = "100px", n.style.borderRadius = "4px", n.style.cursor = "pointer";
    const r = this.identityEpoch;
    this.attachmentUrl(e).then((a) => {
      r === this.identityEpoch && (n.src = a, n.addEventListener("click", () => this.openZoomModal(a)));
    }).catch(() => {
      n.replaceWith(l("span", "fb-muted", `${s}（无法加载）`));
    }), t.append(n);
  }
  /** 以 Bearer 方式取回附件并触发保存/下载（宿主 bridge / 浏览器下载）。 */
  downloadAttachment(t, e) {
    const s = this.identityEpoch;
    Kt(this.effectiveApiBase, this.accessToken, t, { fetchFn: this.apiFetch }).then(async (n) => {
      if (s !== this.identityEpoch) return;
      const r = n.type || "application/octet-stream";
      let a;
      try {
        a = await Ui(n);
      } catch {
      }
      await this.saveAttachment({ name: e, mimeType: r, dataUrl: a }, n);
    }).catch(() => {
    });
  }
  /** 清空回复草稿图片（释放对象 URL）。 */
  clearReplyImages() {
    for (const t of this.dialogueReplyImages)
      t.url && URL.revokeObjectURL(t.url);
    this.dialogueReplyImages = [];
  }
  dialogueReplyText = "";
  dialogueReplyLogs = [];
  dialogueMessagesBox = null;
  dialogueStatusBadge = null;
  dialogueResolutionNoteEl = null;
  async loadDialogue(t, e = !1) {
    if (!(!this.tokenValid() || !this.effectiveApiBase)) {
      if (!e) {
        this.dialogueView.innerHTML = "";
        const s = l("div", void 0, "加载对话中…");
        s.style.padding = "20px", s.style.textAlign = "center", s.style.color = "var(--fb-text-secondary)", this.dialogueView.append(s);
      }
      try {
        const s = await qe(this.effectiveApiBase, this.accessToken, t, { fetchFn: this.apiFetch });
        if (this.activeDialogueId !== t) return;
        const n = s.messages.length > 0 ? Math.max(...s.messages.map((S) => S.seq)) : 0;
        if (n > s.lastReadSeq && (He(this.effectiveApiBase, this.accessToken, t, n, { fetchFn: this.apiFetch }).catch(() => {
        }), this.pollSummary()), e && this.dialogueMessagesBox && this.dialogueStatusBadge) {
          if (this.dialogueStatusBadge.textContent = Tt[s.feedback.issueStatus] ?? s.feedback.issueStatus, this.dialogueStatusBadge.className = `fb-tag ${s.feedback.issueStatus === "waiting_admin" ? "waiting-admin" : s.feedback.issueStatus === "waiting_user" ? "waiting-user" : s.feedback.issueStatus === "resolved" ? "resolved" : ""}`, this.dialogueResolutionNoteEl) {
            const S = s.feedback.resolutionNote;
            this.dialogueResolutionNoteEl.hidden = !S, this.dialogueResolutionNoteEl.textContent = S ? `处理说明：${S}` : "";
          }
          this.appendDialogueMessages(this.dialogueMessagesBox, s);
          return;
        }
        this.dialogueView.innerHTML = "", this.revokeAttachmentUrls(), this.dialogueRenderedSeq = 0;
        const r = l("div", "fb-dialogue-header"), a = l("button", "fb-btn-secondary", "← 返回");
        a.type = "button", a.style.padding = "2px 8px", a.style.fontSize = "12px", a.addEventListener("click", () => this.closeDialogue());
        const o = l("span", void 0, s.feedback.title || s.feedback.text.slice(0, 15) || "反馈详情");
        o.style.fontWeight = "600", o.style.fontSize = "13px", o.style.flex = "1", o.style.overflow = "hidden", o.style.textOverflow = "ellipsis", o.style.whiteSpace = "nowrap", this.dialogueStatusBadge = l(
          "span",
          `fb-tag ${s.feedback.issueStatus === "waiting_admin" ? "waiting-admin" : s.feedback.issueStatus === "waiting_user" ? "waiting-user" : s.feedback.issueStatus === "resolved" ? "resolved" : ""}`,
          Tt[s.feedback.issueStatus] ?? s.feedback.issueStatus
        ), r.append(a, o, this.dialogueStatusBadge), this.dialogueResolutionNoteEl = l("p", "fb-resolution-note");
        const h = s.feedback.resolutionNote;
        this.dialogueResolutionNoteEl.hidden = !h, this.dialogueResolutionNoteEl.textContent = h ? `处理说明：${h}` : "", this.dialogueMessagesBox = l("div", "fb-dialogue-messages"), this.renderDialogueMessages(this.dialogueMessagesBox, s);
        const d = l("div", "fb-dialogue-reply-box"), c = l("textarea", "fb-textarea");
        c.rows = 2, c.placeholder = "向管理员补充信息或提问…", c.value = this.dialogueReplyText, c.addEventListener("input", () => {
          this.dialogueReplyText = c.value;
        }), c.addEventListener("paste", (S) => {
          const I = Array.from(S.clipboardData?.files ?? []).filter(
            (M) => /^image\//.test(M.type)
          );
          I.length !== 0 && (S.preventDefault(), B(I));
        });
        const u = l("div");
        u.style.display = "flex", u.style.gap = "8px", u.style.alignItems = "center", u.style.flexWrap = "wrap";
        const f = l("input");
        f.type = "file", f.accept = "image/png,image/jpeg,image/webp,.png,.jpg,.jpeg,.webp", f.multiple = !0, f.hidden = !0;
        const x = l("input");
        x.type = "file", x.multiple = !0, x.accept = ".log,.txt,.json,.jsonl", x.hidden = !0;
        const b = l("button", "fb-btn-secondary", "添加图片");
        b.type = "button", b.style.padding = "2px 8px", b.style.fontSize = "11px", b.addEventListener("click", () => f.click());
        const m = l("button", "fb-btn-secondary", "添加日志");
        m.type = "button", m.style.padding = "2px 8px", m.style.fontSize = "11px", m.addEventListener("click", () => x.click());
        const A = l("div", "fb-reply-images");
        A.style.display = "flex", A.style.gap = "4px", A.style.flexWrap = "wrap";
        const v = l("p", "fb-shot-error");
        v.hidden = !0, v.setAttribute("role", "alert");
        const T = () => {
          A.innerHTML = "", this.dialogueReplyImages.forEach((S) => {
            const I = l("span", "fb-reply-thumb"), M = l("img", "fb-reply-thumb-img");
            M.src = S.url, M.alt = `回复图片：${S.filename}`, M.title = S.filename, M.addEventListener("click", () => this.openZoomModal(S.url));
            const P = l("button", "fb-thumb-edit", "编辑");
            P.type = "button", P.setAttribute("aria-label", `编辑回复图片 ${S.filename}`), P.addEventListener("click", (q) => {
              q.stopPropagation(), this.openReplyEditor(S.id, T);
            });
            const U = l("button", "fb-thumb-remove", "✕");
            U.type = "button", U.setAttribute("aria-label", `移除回复图片 ${S.filename}`), U.addEventListener("click", (q) => {
              q.stopPropagation(), this.removeReplyImage(S.id), T();
            }), I.append(M, P, U), A.append(I);
          }), this.dialogueReplyLogs.forEach((S, I) => {
            const M = l("span", "fb-tag", `${S.name} ✕`);
            M.style.cursor = "pointer", M.addEventListener("click", () => {
              this.dialogueReplyLogs.splice(I, 1), T();
            }), A.append(M);
          });
        }, B = async (S) => {
          v.hidden = !0;
          for (const I of S) {
            if (this.dialogueReplyImages.length >= this.maxImages) {
              v.hidden = !1, v.textContent = `回复最多附加 ${this.maxImages} 张图片`;
              break;
            }
            const M = I instanceof File ? I.name : "";
            try {
              const P = await Xt(I);
              this.dialogueReplyImages.push({
                id: ft(),
                blob: P.blob,
                url: URL.createObjectURL(P.blob),
                source: "manual",
                origin: "manual",
                filename: pt(M, P.mime, `reply-${this.dialogueReplyImages.length + 1}.png`),
                mime: P.mime,
                width: P.width,
                height: P.height,
                oversize: P.oversize
              });
            } catch {
              v.hidden = !1, v.textContent = `${M || "所选图片"}：仅支持 PNG / JPEG / WebP 静态图片`;
            }
          }
          T();
        };
        f.addEventListener("change", () => {
          const S = Array.from(f.files ?? []);
          f.value = "", B(S);
        }), x.addEventListener("change", () => {
          if (x.files) {
            for (const S of Array.from(x.files))
              this.dialogueReplyLogs.push(S);
            x.value = "", T();
          }
        }), T();
        const k = l("button", "fb-submit", "发送回复");
        k.type = "button", k.style.marginTop = "4px", k.addEventListener("click", async () => {
          const S = c.value.trim();
          if (!(!S && this.dialogueReplyImages.length === 0 && this.dialogueReplyLogs.length === 0)) {
            k.disabled = !0, k.textContent = "发送中…", c.disabled = !0;
            try {
              await De(this.effectiveApiBase, this.accessToken, t, {
                text: S,
                // v12：回复图片走 images[]（有序描述符 + 部件）
                images: this.dialogueReplyImages.length > 0 ? this.dialogueReplyImages.map(Ot) : void 0,
                logs: this.dialogueReplyLogs.length > 0 ? this.dialogueReplyLogs : void 0
              }, { fetchFn: this.apiFetch }), this.dialogueReplyText = "", this.clearReplyImages(), this.dialogueReplyLogs = [], c.value = "", T(), await this.loadDialogue(t), this.pollSummary();
            } catch (I) {
              v.hidden = !1, v.textContent = I instanceof Error ? I.message : "发送失败";
            } finally {
              k.disabled = !1, k.textContent = "发送回复", c.disabled = !1;
            }
          }
        }), u.append(f, x, b, m), d.append(c, A, v, u, k), this.dialogueView.append(r, this.dialogueResolutionNoteEl, this.dialogueMessagesBox, d);
      } catch (s) {
        if (!e) {
          this.dialogueView.innerHTML = "";
          const n = l("div", "fb-error");
          n.style.padding = "16px", n.style.textAlign = "center", n.textContent = s instanceof Error ? s.message : "加载对话失败", this.dialogueView.append(n);
        }
      }
    }
  }
  /** 移除一张回复草稿图片（释放对象 URL）。 */
  removeReplyImage(t) {
    const e = this.dialogueReplyImages.findIndex((n) => n.id === t);
    if (e === -1) return;
    const [s] = this.dialogueReplyImages.splice(e, 1);
    s?.url && URL.revokeObjectURL(s.url);
  }
  /** 编辑回复草稿图片：保存原位替换（不占名额），取消不动。 */
  async openReplyEditor(t, e) {
    if (this.editorHandle) return;
    const s = this.dialogueReplyImages.find((r) => r.id === t);
    if (!s) return;
    const n = this.identityEpoch;
    try {
      const r = await vt(s.blob);
      if (n !== this.identityEpoch) return;
      this.editorHandle = ie({
        image: r.source,
        width: r.width,
        height: r.height,
        mount: this.root,
        onCancel: () => {
          this.editorHandle = null, r.close?.();
        },
        onSave: async (a) => {
          const o = await Dt(a);
          n !== this.identityEpoch || !this.dialogueReplyImages.includes(s) || o && (URL.revokeObjectURL(s.url), s.blob = o.blob, s.url = URL.createObjectURL(o.blob), s.source = "edited", s.mime = o.mime, s.width = o.width, s.height = o.height, s.oversize = o.oversize, s.filename = pt(s.filename, o.mime, `edited-${s.id}.png`), this.editorHandle?.close(), this.editorHandle = null, r.close?.(), e());
        }
      });
    } catch {
    }
  }
  /**
   * 渲染完整对话（非轮询 / 首次打开）：清空后重建原始反馈块 + 全部消息，
   * 记录已渲染的最大 seq 供增量追加。
   */
  renderDialogueMessages(t, e) {
    t.innerHTML = "", this.dialogueRenderedSeq = 0;
    const s = l("div", "fb-dialogue-msg user"), n = l("div", "fb-dialogue-msg-meta");
    n.append(
      l("span", void 0, "我 (原始反馈)"),
      l("span", void 0, new Date(e.feedback.createdAt).toLocaleString())
    );
    const r = l("div", void 0, e.feedback.text);
    r.style.whiteSpace = "pre-wrap", s.append(n, r);
    const a = Array.isArray(e.feedback.images) ? e.feedback.images : [];
    if (a.length > 0) {
      const o = l("div", "fb-dialogue-attachments");
      o.style.marginTop = "6px", o.style.display = "flex", o.style.gap = "6px", o.style.flexWrap = "wrap";
      for (const h of a)
        this.renderAttachmentImage(
          o,
          `/api/feedback/${e.feedback.id}/images/${h.id}`,
          h.filename || "反馈图片"
        );
      s.append(o);
    } else e.feedback.screenshot && this.renderAttachmentImage(
      s,
      `/api/feedback/${e.feedback.id}/screenshot`,
      "反馈截图"
    );
    if (e.feedback.logs && e.feedback.logs.length > 0) {
      const o = l("div");
      o.style.marginTop = "6px", o.style.display = "flex", o.style.gap = "6px", o.style.flexWrap = "wrap";
      for (const h of e.feedback.logs) {
        const d = l("button", "fb-btn-secondary", `${h.filename} (${Ft(h.byteSize)})`);
        d.type = "button", d.style.fontSize = "11px", d.style.padding = "2px 6px";
        const c = `/api/feedback/${e.feedback.id}/logs/${h.id}/download`;
        d.addEventListener("click", () => this.downloadAttachment(c, h.filename)), o.append(d);
      }
      s.append(o);
    }
    t.append(s), this.appendDialogueMessages(t, e), t.scrollTop = t.scrollHeight;
  }
  /**
   * 增量渲染（轮询）：只追加 seq 更大的新消息；用户正在阅读时不强跳滚动
   * （已在底部才跟随到底），回复草稿与滚动位置保持不动。
   */
  appendDialogueMessages(t, e) {
    const s = t.scrollHeight - t.scrollTop - t.clientHeight < 8;
    let n = !1;
    for (const r of e.messages) {
      if (r.seq <= this.dialogueRenderedSeq) continue;
      this.dialogueRenderedSeq = Math.max(this.dialogueRenderedSeq, r.seq), n = !0;
      const a = r.senderType === "admin" ? "fb-dialogue-msg admin" : r.senderType === "system" ? "fb-dialogue-msg system" : "fb-dialogue-msg user", o = l("div", a), h = l("div", "fb-dialogue-msg-meta"), d = r.senderType === "admin" ? `管理员 (${r.senderName})` : r.senderType === "system" ? "系统" : `我 (#${r.seq})`;
      h.append(l("span", void 0, d), l("span", void 0, new Date(r.createdAt).toLocaleString()));
      const c = l("div", void 0, r.text);
      if (c.style.whiteSpace = "pre-wrap", o.append(h, c), r.resolutionNote) {
        const u = l("div", "fb-resolution-note", `处理说明：${r.resolutionNote}`);
        o.append(u);
      }
      if (r.attachments && r.attachments.length > 0) {
        const u = l("div");
        u.style.marginTop = "6px", u.style.display = "flex", u.style.gap = "6px", u.style.flexWrap = "wrap";
        for (const f of r.attachments) {
          const x = `/api/feedback/messages/attachments/${f.id}`;
          if (f.kind === "screenshot" || f.kind === "image")
            this.renderAttachmentImage(u, x, f.filename || "附件图片");
          else {
            const b = l("button", "fb-btn-secondary", `${f.filename} (${Ft(f.byteSize)})`);
            b.type = "button", b.style.fontSize = "11px", b.style.padding = "2px 6px", b.addEventListener("click", () => this.downloadAttachment(x, f.filename)), u.append(b);
          }
        }
        o.append(u);
      }
      t.append(o);
    }
    n && s && (t.scrollTop = t.scrollHeight);
  }
  // ---------- 日志附件处理 ----------
  /** 日志摘要行文案（T4）：默认只显示「已附诊断日志 / 暂无日志」这类简短摘要。 */
  logsSummaryText() {
    return this.logsCollecting ? "正在采集诊断日志…" : this.draft.logs.length > 0 ? `已附诊断日志（${this.draft.logs.length}）` : this.draft.logsCollected ? "暂无日志" : "诊断日志";
  }
  setLogsOpen(t) {
    this.logsOpen = t, this.logsBody && (this.logsBody.hidden = !t), this.logsToggleBtn && this.logsToggleBtn.setAttribute("aria-expanded", String(t));
  }
  toggleLogs() {
    this.setLogsOpen(!this.logsOpen);
  }
  async collectLogs() {
    const t = this._logProvider;
    if (!t || this.logsCollecting) return;
    const e = this.identityEpoch, s = this.draft, n = ++this.logOpSeq;
    this.logsCollecting = !0, this.logStatusEl.hidden = !1, this.logStatusEl.textContent = "正在自动采集日志…", this.logErrorEl.hidden = !0, this.logErrorEl.textContent = "", this.syncLogsUi();
    let r = null;
    const a = () => {
      r !== null && this.logTimeout === r && (clearTimeout(r), this.logTimeout = null);
    }, o = new Error("采集日志超时（超过 3 秒）");
    try {
      const h = new Promise((x, b) => {
        r = setTimeout(() => b(o), 3e3), this.logTimeout = r;
      }), d = Promise.resolve(t()), c = await Promise.race([d, h]);
      if (a(), !this.isConnected || this.draft !== s || e !== this.identityEpoch || n !== this.logOpSeq || !this.openState || this.phase === "submitting")
        return;
      let u = 0, f = !1;
      if (c) {
        const x = Array.isArray(c) ? c : [c];
        for (const b of x) {
          if (!b || !b.filename || !b.blob) continue;
          if (this.draft.logs.length >= Q) {
            f = !0, this.logErrorEl.hidden = !1, this.logErrorEl.textContent = `最多附加 ${Q} 个日志文件`;
            break;
          }
          const m = fe(b.filename);
          if (!ue.has(m)) {
            f = !0, this.logErrorEl.hidden = !1, this.logErrorEl.textContent = `日志 ${b.filename} 格式不受支持（仅支持 .log / .txt / .json / .jsonl）`;
            continue;
          }
          if (b.blob.size > ce) {
            f = !0, this.logErrorEl.hidden = !1, this.logErrorEl.textContent = `日志 ${b.filename} 超过 1MiB 限制`;
            continue;
          }
          if (b.blob.size === 0) {
            f = !0, this.logErrorEl.hidden = !1, this.logErrorEl.textContent = `日志 ${b.filename} 不能为空文件`;
            continue;
          }
          this.draft.logs.push({
            filename: b.filename,
            source: "auto",
            blob: b.blob,
            byteSize: b.blob.size
          }), this.draft.version++, u++;
        }
      }
      if (!this.isConnected || this.draft !== s || e !== this.identityEpoch || n !== this.logOpSeq || !this.openState)
        return;
      this.draft.logsCollected = !0, u > 0 ? (this.logStatusEl.hidden = !1, this.logStatusEl.textContent = "已附诊断日志") : f ? (this.logStatusEl.hidden = !0, this.logStatusEl.textContent = "") : (this.logStatusEl.hidden = !1, this.logStatusEl.textContent = "当前会话暂无日志");
    } catch (h) {
      if (a(), !this.isConnected || this.draft !== s || e !== this.identityEpoch || n !== this.logOpSeq || !this.openState || this.phase === "submitting")
        return;
      const d = h === o ? "日志获取失败，可继续提交（采集超时）" : "日志获取失败，可继续提交";
      this.logErrorEl.hidden = !1, this.logErrorEl.textContent = d, this.logErrorEl.append(this.logRetryBtn), this.logStatusEl.hidden = !0, this.logStatusEl.textContent = "";
    } finally {
      a(), this.isConnected && this.draft === s && e === this.identityEpoch && n === this.logOpSeq && (this.logsCollecting = !1, this.syncLogsUi(), this.syncUi());
    }
  }
  handleLogFileSelect() {
    if (this.phase === "submitting") return;
    const t = Array.from(this.logFileInput.files || []);
    if (t.length !== 0) {
      this.logErrorEl.hidden = !0, this.logErrorEl.textContent = "";
      for (const e of t) {
        if (this.draft.logs.length >= Q) {
          this.logErrorEl.hidden = !1, this.logErrorEl.textContent = `最多附加 ${Q} 个日志文件`;
          break;
        }
        const s = fe(e.name);
        if (!ue.has(s)) {
          this.logErrorEl.hidden = !1, this.logErrorEl.textContent = `日志 ${e.name} 格式不受支持（仅支持 .log / .txt / .json / .jsonl）`;
          continue;
        }
        if (e.size > ce) {
          this.logErrorEl.hidden = !1, this.logErrorEl.textContent = `日志 ${e.name} 超过 1MiB 限制`;
          continue;
        }
        if (e.size === 0) {
          this.logErrorEl.hidden = !1, this.logErrorEl.textContent = `日志 ${e.name} 不能为空文件`;
          continue;
        }
        this.draft.logs.push({
          filename: e.name,
          source: "manual",
          blob: e,
          byteSize: e.size
        }), this.draft.version++;
      }
      this.logFileInput.value = "", this.syncLogsUi(), this.syncUi();
    }
  }
  removeLog(t) {
    t >= 0 && t < this.draft.logs.length && (this.draft.logs.splice(t, 1), this.draft.version++, this.draft.logs.length === 0 && !this.logsCollecting && (this.logStatusEl.hidden = !0, this.logStatusEl.textContent = ""), this.syncLogsUi(), this.syncUi());
  }
  async openLogPreview(t) {
    this.logPreviewTitle.textContent = t.filename, this.logPreviewBody.textContent = "读取中…", this.logPreviewModal.classList.add("is-open");
    try {
      const e = await _i(t.blob);
      this.logPreviewBody.textContent = e;
    } catch {
      this.logPreviewBody.textContent = "读取日志内容失败";
    }
  }
  closeLogPreview() {
    this.logPreviewModal && this.logPreviewModal.classList.remove("is-open");
  }
  syncLogsUi() {
    const t = this.draft.logs;
    this.logsCountEl.textContent = ` (${t.length}/${Q})`;
    const e = this.phase === "submitting" || this.polling || this.logsCollecting;
    this.logsSummaryEl.textContent = this.logsSummaryText(), this.recollectLogBtn.hidden = !this._logProvider || e, this.recollectLogBtn.disabled = e, this.setLogsOpen(this.logsOpen), this.logErrorEl.hidden || this.setLogsOpen(!0), this.addLogBtn.disabled = e || t.length >= Q, this.addLogBtn.hidden = t.length >= Q, this.logRetryBtn.disabled = e, this.logsList.innerHTML = "";
    for (let s = 0; s < t.length; s++) {
      const n = t[s];
      if (!n) continue;
      const r = l("li", "fb-log-item"), a = l("div", "fb-log-info"), o = l("span", "fb-log-name", n.filename);
      o.title = n.filename;
      const h = l("span", "fb-log-badge", n.source === "auto" ? "自动" : "手动"), d = l("span", "fb-log-size", Ft(n.byteSize || n.blob.size));
      a.append(o, h, d);
      const c = l("div", "fb-log-btns"), u = l("button", "fb-log-btn-preview", "预览");
      u.type = "button", u.setAttribute("aria-label", `预览 ${n.filename}`), u.addEventListener("click", () => {
        this.openLogPreview(n);
      });
      const f = l("button", "fb-log-btn-remove", "删除");
      f.type = "button", f.setAttribute("aria-label", `删除 ${n.filename}`), f.disabled = e, f.addEventListener("click", () => {
        this.removeLog(s);
      }), c.append(u, f), r.append(a, c), this.logsList.append(r);
    }
  }
  // ---------- 提交流程与认证 ----------
  tokenValid() {
    return this.accessToken !== null && Date.now() < this.tokenExpiresAt;
  }
  failPhase(t) {
    this.phase = "failed", this.lastErrorSummary = t, this.renderStatus(""), this.syncUi();
  }
  async onPrimaryAction() {
    if (!(this.phase === "submitting" || this.polling)) {
      if (!this.tokenValid()) {
        if (gt(this.textarea.value) === 0) {
          this.textarea.focus();
          return;
        }
        this.beginLogin(!0);
        return;
      }
      await this.submit();
    }
  }
  /** 4xx 视为服务端明确处理并拒绝（未保存）；其余（网络错误/5xx/408/429）结果未知。 */
  isUnknownOutcome(t) {
    return t instanceof H ? t.status >= 500 || t.status === 408 || t.status === 429 : !0;
  }
  async submit() {
    if (this.phase === "submitting") return;
    const t = this.identityEpoch, e = this.effectiveApiBase, s = this.appId;
    if (!e || !s) {
      this.failPhase("缺少必填配置：api-base / app-id");
      return;
    }
    const n = this.draft.images.map(Ot), r = this.submitSnapshot, a = r !== null && r.unknownOutcome && r.draftVersion === this.draft.version && r.text === this.textarea.value && Ut(r.images, n) && zt(r.logs, this.draft.logs);
    if (this.quota !== null && this.quota.unlimited !== !0 && this.quota.remaining <= 0 && !a) {
      this.phase = "quota", this.lastErrorSummary = "今日提交次数已用完，请在额度刷新后重试。文字与图片已保留。", this.renderStatus(""), this.syncUi();
      return;
    }
    if (this.draft.images.length > this.maxImages) {
      this.failPhase(
        `当前服务端仅支持单张图片（已选择 ${this.draft.images.length} 张）：请移除多余图片，或升级服务端后再提交。图片与文字已保留。`
      );
      return;
    }
    let o = this.submitSnapshot;
    const h = o !== null && o.draftVersion === this.draft.version && o.text === this.textarea.value && Ut(o.images, n) && zt(o.logs, this.draft.logs) && o.apiBase === e && o.appId === s;
    if (!o || !h) {
      o && o.unknownOutcome && (this.unconfirmedRequest = {
        key: o.key,
        capturedAt: o.captureInfo?.capturedAt ?? null,
        textSummary: o.text.slice(0, 40)
      });
      const u = this.textarea.value, f = gt(u);
      if (f < 1 || f > Y) {
        this.failPhase(`反馈内容需为 1–${Y} 字`);
        return;
      }
      const x = {};
      this.appVersion && (x.appVersion = this.appVersion), this.pageLabel && (x.pageLabel = this.pageLabel);
      const b = this.captureImage();
      o = {
        key: ft(),
        apiBase: e,
        appId: s,
        context: x,
        text: u,
        images: n,
        captureInfo: b?.captureInfo ? { ...b.captureInfo } : null,
        logs: [...this.draft.logs],
        draftVersion: this.draft.version,
        unknownOutcome: !1
      }, this.submitSnapshot = o, this.idempotencyKey = o.key;
    }
    const d = o;
    if (this.lastSubmittedText = d.text, !this.tokenValid()) {
      this.beginLogin(!0);
      return;
    }
    this.invalidateCaptureSession(), this.captureInFlight = null, this.restoreCaptureUi(), this.phase = "submitting", this.lastErrorSummary = null, this.waitingNotice = null, this.renderStatus("提交中…"), this.syncUi();
    const c = this.accessToken;
    try {
      if (!this.capabilitiesKnown && d.images.length >= 2) {
        if (await this.probeFeatures(), t !== this.identityEpoch) return;
        if (this.capabilitiesKnown && d.images.length > this.maxImages) {
          this.phase = "failed", d.unknownOutcome = !1, this.lastErrorSummary = `当前服务端最多支持 ${this.maxImages} 张图片。请删除多余图片后重试，或升级服务端以支持多图。`, this.renderStatus(""), this.syncUi();
          return;
        }
      }
      const u = d.images.length >= 2, f = await ze(d.apiBase, this.accessToken, {
        idempotencyKey: d.key,
        appId: d.appId,
        ...this.appName ? { appName: this.appName } : {},
        text: d.text,
        ...Object.keys(d.context).length ? { context: d.context } : {},
        ...d.captureInfo ? { capture: d.captureInfo } : {},
        ...u ? { images: d.images } : d.images[0] ? { screenshot: d.images[0].blob } : {},
        ...d.logs.length > 0 ? { logs: d.logs } : {}
      }, { fetchFn: this.apiFetch });
      if (t !== this.identityEpoch) return;
      this.invalidateQuotaRefresh(), f.quota && (this.quota = f.quota), f.user && (this.authUser = f.user), this.lastFeedbackId = f.feedbackId, this.submitSnapshot = null, this.idempotencyKey = null, this.unconfirmedRequest = null, this.clearDraft(), this.textarea.value = "", this.authRequired = !1, this.lastRecord = {
        id: f.feedbackId,
        status: f.status,
        createdAt: "",
        updatedAt: "",
        errorSummary: null,
        kaneoUrl: null
      }, this.phase = "tracking", this.waitingNotice = ut(f.collectionState), this.renderStatus(this.waitingNotice ?? "已保存，正在整理"), this.dispatchEvent(
        new CustomEvent("feedback-submitted", {
          detail: { feedbackId: f.feedbackId, status: f.status, replayed: f.replayed === !0 },
          bubbles: !0,
          composed: !0
        })
      ), this.syncUi(), this.waitingNotice === null && this.startPolling(f.feedbackId), this.consumeQuotaRefreshIntent();
    } catch (u) {
      if (t !== this.identityEpoch) return;
      if (u instanceof H && u.status === 401) {
        if (c !== this.accessToken) {
          this.phase = "idle", this.renderStatus("登录已更新，请重新提交"), this.syncUi();
          return;
        }
        this.accessToken = null, this.tokenExpiresAt = 0, this.authRequired = !0, this.phase = "idle", this.invalidateQuotaRefresh(), this.quotaRefreshPending = !1, this.renderStatus("需要登录（登录已过期）"), this.syncUi(), this.clearHostSession();
        return;
      }
      if (this.phase = "failed", u instanceof H && u.code === "daily_quota_exceeded") {
        d.unknownOutcome = !1, this.invalidateQuotaRefresh(), u.quota && (this.quota = u.quota), this.phase = "quota", this.lastErrorSummary = "今日提交次数已用完，请在额度刷新后重试。文字与截图已保留。", this.renderStatus(""), this.syncUi(), this.consumeQuotaRefreshIntent();
        return;
      }
      u instanceof H && u.code === "idempotency_conflict" ? (d.unknownOutcome = !1, this.lastErrorSummary = "提交冲突：同一提交标识已对应不同内容。请修改内容重新提交，或“再记一条”。") : (d.unknownOutcome = this.isUnknownOutcome(u), this.lastErrorSummary = u instanceof Error ? u.message : String(u)), this.renderStatus(""), this.syncUi();
    }
  }
  // ---------- 轮询 ----------
  startPolling(t) {
    this.stopPolling(), this.polling = !0, this.pollDelay = yt, this.pollStartedAt = Date.now(), this.syncUi(), this.schedulePoll(t);
  }
  schedulePoll(t) {
    this.polling && (this.pollTimer = setTimeout(() => {
      this.pollTick(t);
    }, this.pollDelay), this.pollDelay = Math.min(this.pollDelay * 2, Oi));
  }
  async pollTick(t) {
    if (!this.polling) return;
    const e = this.identityEpoch, s = this.effectiveApiBase;
    if (!s || !this.tokenValid()) {
      this.polling = !1, this.tokenValid() || (this.authRequired = !0, this.renderStatus("登录已过期，请重新登录后继续查看进度。"), this.syncUi());
      return;
    }
    let n;
    const r = this.accessToken;
    try {
      n = await Wt(s, r, t, { fetchFn: this.apiFetch });
    } catch (a) {
      if (e !== this.identityEpoch) return;
      if (a instanceof H && a.status === 401) {
        if (r !== this.accessToken) {
          this.schedulePoll(t);
          return;
        }
        this.polling = !1, this.accessToken = null, this.authRequired = !0, this.renderStatus("登录已过期，请重新登录后继续查看进度。"), this.syncUi(), this.clearHostSession();
        return;
      }
      if (a instanceof H && a.status === 404) {
        this.polling = !1, this.phase = "failed", this.lastErrorSummary = "未找到反馈记录", this.renderStatus(""), this.syncUi();
        return;
      }
      n = this.lastRecord ?? {
        id: t,
        status: "received",
        createdAt: "",
        updatedAt: "",
        errorSummary: null,
        kaneoUrl: null
      };
    }
    if (e === this.identityEpoch && this.polling) {
      if (de(n.status)) {
        this.stopPolling(), this.applyRecordState(n);
        return;
      }
      if (this.lastRecord = n, ut(n.collectionState) !== null) {
        this.stopPolling(), this.applyRecordState(n);
        return;
      }
      if (Date.now() - this.pollStartedAt + this.pollDelay > qi) {
        this.polling = !1, this.renderStatus("已保存，后台正在整理中，稍后可在管理页查看。"), this.syncUi();
        return;
      }
      this.renderStatus("已保存，正在整理"), this.schedulePoll(t);
    }
  }
  /**
   * 依记录状态更新阶段 / 结果 / 状态文本（轮询与手动刷新共用）。
   * 返回 true 表示已进入终态（调用方应停止轮询）。
   */
  applyRecordState(t) {
    this.lastRecord = t;
    const e = ut(t.collectionState);
    return e !== null ? (this.waitingNotice = e, this.phase = "tracking", this.lastErrorSummary = null, this.renderStatus(e), this.syncUi(), !0) : (this.waitingNotice = null, t.status === "archived" ? (this.phase = "archived", this.renderStatus("已归档"), this.syncUi(), !0) : t.status === "failed" ? (this.phase = "failed", this.lastErrorSummary = t.errorSummary ?? null, this.renderStatus(""), this.syncUi(), !0) : t.status === "needs_review" ? (this.phase = "needs_review", this.lastErrorSummary = null, this.renderStatus(""), this.syncUi(), !0) : !1);
  }
  async resumePolling() {
    this.lastFeedbackId && !this.polling && this.tokenValid() && this.startPolling(this.lastFeedbackId);
  }
  stopPolling() {
    this.polling = !1, this.pollTimer !== null && (clearTimeout(this.pollTimer), this.pollTimer = null);
  }
  /**
   * 服务端已接收但后台处理失败时的手动刷新：只读 `GET /api/feedback/:id`，
   * 更新 phase / lastRecord / lastErrorSummary / 状态文本。绝不重复 POST。
   * 与轮询共用身份世代校验：身份切换后的迟到结果一律丢弃。
   */
  async refreshLastRecord() {
    const t = this.lastFeedbackId;
    if (!t || this.refreshing) return;
    const e = this.effectiveApiBase;
    if (!e) {
      this.failPhase("缺少必填配置：api-base / app-id");
      return;
    }
    if (!this.tokenValid()) {
      this.authRequired = !0, this.renderStatus("需要登录（登录已过期）"), this.syncUi();
      return;
    }
    const s = this.identityEpoch, n = this.accessToken;
    this.refreshing = !0, this.renderStatus("正在刷新状态…"), this.syncUi();
    try {
      const r = await Wt(e, n, t);
      if (s !== this.identityEpoch) return;
      if (de(r.status)) {
        this.stopPolling(), this.applyRecordState(r);
        return;
      }
      if (ut(r.collectionState) !== null) {
        this.stopPolling(), this.applyRecordState(r);
        return;
      }
      this.waitingNotice = null, this.lastRecord = r, this.phase = "tracking", this.renderStatus("已保存，正在整理"), this.syncUi(), this.polling || this.startPolling(t);
    } catch (r) {
      if (s !== this.identityEpoch) return;
      if (r instanceof H && r.status === 401) {
        if (n !== this.accessToken)
          return;
        this.accessToken = null, this.tokenExpiresAt = 0, this.authRequired = !0, this.renderStatus("登录已过期，请重新登录后继续查看进度。"), this.syncUi(), this.clearHostSession();
        return;
      }
      if (r instanceof H && r.status === 404) {
        this.lastErrorSummary = "未找到反馈记录", this.renderStatus("未找到反馈记录。"), this.syncUi();
        return;
      }
      this.lastErrorSummary = r instanceof Error ? r.message : String(r), this.renderStatus("刷新失败，请稍后再试。"), this.syncUi();
    } finally {
      this.refreshing = !1, this.syncUi();
    }
  }
  /**
   * 复制反馈标识：剪贴板不可用（或写入失败）时静默降级，绝不抛错、绝不发请求。
   */
  copyFeedbackId() {
    const t = this.lastFeedbackId;
    if (t)
      try {
        const e = typeof navigator > "u" ? void 0 : navigator.clipboard;
        if (!e || typeof e.writeText != "function") return;
        Promise.resolve(e.writeText(t)).then(() => this.renderStatus("反馈标识已复制")).catch(() => {
        });
      } catch {
      }
  }
  // ---------- 服务器设置（T6） ----------
  /** 按当前（appId, api-base）槽位读取本机覆盖；键未变时跳过，保留会话内未落盘的选择。 */
  loadServerOverride() {
    const t = Ee(this.appId ?? "", this.apiBase ?? "");
    if (t !== this.serverOverrideKeyLoaded) {
      this.serverOverrideKeyLoaded = t;
      try {
        const e = window.localStorage.getItem(t);
        if (e === null) {
          this.serverOverride = null;
          return;
        }
        const s = nt(e);
        this.serverOverride = s.ok ? s.base : null;
      } catch {
        this.serverOverride = null, this.serverPrefWritable = !1;
      }
    }
  }
  /** 两个地址规范化后是否相同（不可规范化的按去空白原值比较）。 */
  sameNormalizedBase(t, e) {
    if (t === null || e === null) return t === e;
    const s = nt(t), n = nt(e);
    return (s.ok ? s.base : t.trim()) === (n.ok ? n.base : e.trim());
  }
  pageIsHttps() {
    return typeof window < "u" && typeof window.location < "u" && window.location.protocol === "https:";
  }
  openServerSettings() {
    this.settingsOpen = !0, this.settingsConfirmOpen = !1, this.pendingServerTarget = void 0, this.settingsInput.value = this.effectiveApiBase ?? "", this.settingsErrorEl.hidden = !0, this.settingsErrorEl.textContent = "", this.settingsHintEl.hidden = !0, this.settingsHintEl.textContent = "", this.syncUi(), this.settingsInput.focus();
  }
  closeServerSettings() {
    this.settingsOpen = !1, this.settingsConfirmOpen = !1, this.pendingServerTarget = void 0, this.syncUi();
  }
  setSettingsHint(t) {
    this.settingsHintEl.textContent = t, this.settingsHintEl.hidden = t === "";
  }
  /**
   * 写入 / 清除本机覆盖偏好；返回是否落盘成功（失败时调用方提示「仅本次生效」）。
   * 偏好只存地址——不持久化草稿、密码或令牌。
   */
  persistServerOverride(t) {
    const e = Li(this.appId ?? "", this.apiBase ?? "", t);
    return e || (this.serverPrefWritable = !1), e;
  }
  /**
   * 保存设置视图输入：校验 → 与当前有效地址相同仅落盘（不触发身份重置）→
   * 不同且有草稿 / 未确认提交时先展开确认块 → 确认后切换。
   */
  saveServerSettings() {
    const t = nt(this.settingsInput.value, { pageIsHttps: this.pageIsHttps() });
    if (!t.ok) {
      this.settingsErrorEl.textContent = t.reason, this.settingsErrorEl.hidden = !1, this.setSettingsHint("");
      return;
    }
    this.settingsErrorEl.hidden = !0, this.settingsErrorEl.textContent = "";
    const e = this.sameNormalizedBase(t.base, this.apiBase) ? null : t.base, s = e ?? this.apiBase;
    if (this.sameNormalizedBase(s, this.effectiveApiBase)) {
      const n = this.persistServerOverride(e);
      this.serverOverride = e, this.setSettingsHint(n ? "已保存。" : "服务器地址仅本次生效（偏好未能保存到本机）。"), this.syncUi();
      return;
    }
    this.requestServerSwitch(e);
  }
  /** 「恢复默认」：与保存共用确认与提交流程，目标覆盖为 null。 */
  requestRestoreDefault() {
    this.sameNormalizedBase(this.apiBase, this.effectiveApiBase) || this.requestServerSwitch(null);
  }
  /** 地址即将变化：有草稿或结果未确认提交时先展开内联确认块，否则直接切换。 */
  requestServerSwitch(t) {
    const e = this.unconfirmedRequest !== null || this.submitSnapshot?.unknownOutcome === !0 || this.phase === "submitting";
    if (this.isDraftDirty() || e || this.polling) {
      this.settingsConfirmOpen = !0, this.pendingServerTarget = t, this.settingsConfirmText.textContent = e ? "切换服务器将清空当前草稿（文字、截图与日志）。已有一条提交的结果尚未确认，旧服务器可能已接收——切换不会撤回，也不会自动向新服务器重发。" : "切换服务器将清空当前草稿（文字、截图与日志）。", this.syncUi();
      return;
    }
    this.commitServerSwitch(t);
  }
  confirmServerSwitch() {
    if (this.pendingServerTarget === void 0) return;
    const t = this.pendingServerTarget;
    this.pendingServerTarget = void 0, this.commitServerSwitch(t);
  }
  cancelServerSwitch() {
    this.settingsConfirmOpen = !1, this.pendingServerTarget = void 0, this.syncUi();
  }
  /**
   * 应用覆盖并切换身份：相同规范化地址不触发重置由调用方保证；
   * 这里落盘偏好 → epoch 递增作废旧服务在途请求 → 完整服务切换重置 → 探测连通性。
   */
  commitServerSwitch(t) {
    const e = this.persistServerOverride(t);
    this.settingsConfirmOpen = !1, this.pendingServerTarget = void 0, this.serverOverride = t, this.identityEpoch++, this.quotaRefreshPending = !1, this.resetForServiceSwitch(), this.settingsOpen = !0, this.setSettingsHint(
      e ? "已保存。" : "服务器地址已切换，但仅本次生效（偏好未能保存到本机）。"
    ), this.syncUi(), this.probeServer(this.effectiveApiBase);
  }
  /**
   * 保存后探测新地址连通性（GET /healthz，不带凭据）：
   * 失败只提示，不回切——连接失败不自动换回其他服务器（T6）。
   */
  async probeServer(t) {
    if (!t) return;
    const e = this.identityEpoch, s = new AbortController(), n = setTimeout(() => s.abort(), 5e3);
    try {
      await this.apiFetch(at(t, "/healthz"), {
        method: "GET",
        signal: s.signal,
        credentials: "omit"
      });
    } catch {
      e === this.identityEpoch && this.settingsOpen && this.setSettingsHint("已保存，但暂时无法连接该服务器，请确认地址可用（不会自动切回）。");
    } finally {
      clearTimeout(n);
    }
  }
  /** 设置视图的只读展示同步（当前有效地址 / 宿主默认 / 确认块可见性）。 */
  syncSettingsUi() {
    if (!this.settingsView) return;
    this.settingsView.hidden = !this.settingsOpen, this.navTabs && (this.navTabs.hidden = this.settingsOpen), this.body && (this.body.hidden = this.settingsOpen || this.activeTab !== "compose"), this.historyContainer && (this.historyContainer.hidden = this.settingsOpen || this.activeTab !== "history"), this.settingsCurrent.textContent = this.effectiveApiBase ?? "—";
    const t = this.apiBase, e = this.serverOverride !== null;
    this.settingsDefaultRow.hidden = !e || t === null, this.settingsDefaultText.textContent = e ? t ?? "" : "", this.settingsRestoreBtn.disabled = !e, this.settingsConfirmEl.hidden = !this.settingsConfirmOpen;
  }
  // ---------- 面板内登录 ----------
  pendingSubmit = !1;
  /** 展开面板内账号密码表单（不打开新窗口）；pendingSubmit=true 时登录成功后只提交一次。 */
  beginLogin(t) {
    const e = this.effectiveApiBase, s = this.appId;
    if (!e || !s) {
      this.failPhase("缺少必填配置：api-base / app-id");
      return;
    }
    this.authRequired = !0, this.pendingSubmit = t, this.loginVisible = !0, this.loginError = "", this.syncUi(), this.loginUsername.focus(), this.renderStatus("需要登录：请输入账号密码");
  }
  /** 取消登录：收起表单并让在途登录失效，草稿与截图保留。 */
  cancelLogin() {
    this.invalidateLogin(), this.loginVisible = !1, this.renderStatus(this.phase === "tracking" ? "已保存，正在整理" : ""), this.syncUi();
  }
  /**
   * 使尚未完成的登录接续失效：递增登录序号，清除密码、登录忙碌状态与
   * 待自动提交标记。**不撤销**已经建立的有效登录态，也不动草稿与截图。
   * 调用点：关闭面板、取消登录、组件卸载、服务身份变化。
   */
  invalidateLogin() {
    this.loginSeq++, this.loginBusy = !1, this.pendingSubmit = !1, this.loginPassword.value = "";
  }
  /**
   * 登录回调是否仍然有效：序号未变（面板自登录发起以来没有被关闭、
   * 也没有被取消 / 卸载 / 换身份）+ 服务身份世代未变 + 组件仍连接。
   */
  loginStillValid(t, e) {
    return t === this.loginSeq && e === this.identityEpoch && this.isConnected;
  }
  async doLogin() {
    if (this.loginBusy) return;
    const t = this.effectiveApiBase, e = this.appId;
    if (!t || !e) {
      this.failPhase("缺少必填配置：api-base / app-id");
      return;
    }
    const s = this.loginUsername.value.trim(), n = this.loginPassword.value;
    if (!s || !n) {
      this.loginError = "请输入用户名和密码", this.syncUi();
      return;
    }
    const r = this.identityEpoch, a = ++this.loginSeq;
    this.loginBusy = !0, this.loginError = "", this.syncUi();
    try {
      const o = await Me(t, { username: s, password: n, clientLabel: Ci(e), appId: e }, { fetchFn: this.apiFetch });
      if (!this.loginStillValid(a, r)) return;
      this.accessToken = o.token;
      const h = Date.parse(o.expiresAt);
      if (this.tokenExpiresAt = Number.isFinite(h) ? h : Date.now() + 15 * 6e4, this.authUser = o.user, this.quota = o.quota ?? null, this.applyCapabilities(o.capabilities), this.sessionStore)
        try {
          Promise.resolve(
            this.sessionStore.saveSession({ accessToken: o.token, expiresAt: o.expiresAt })
          ).catch(() => {
          });
        } catch {
        }
      this.authRequired = !1, this.loginVisible = !1, this.loginBusy = !1, this.loginError = "", this.quotaRefreshFailed = !1, this.loginPassword.value = "", this.renderStatus("已登录。"), this.syncUi(), this.startSummaryPolling(), this.consumeQuotaRefreshIntent();
      const d = this.pendingSubmit;
      this.pendingSubmit = !1, d && gt(this.textarea.value) > 0 ? this.submit() : this.phase === "tracking" && this.resumePolling();
    } catch (o) {
      if (!this.loginStillValid(a, r)) return;
      this.loginBusy = !1, this.loginPassword.value = "", this.loginError = Ri(o), this.renderStatus(""), this.syncUi();
    }
  }
  // ---------- 额度刷新与生命周期 ----------
  cancelQuotaTimer() {
    this.quotaTimer !== null && (clearTimeout(this.quotaTimer), this.quotaTimer = null);
  }
  /**
   * 使在途额度查询失效并取消定时查询：关闭 / 卸载 / 退到后台 / 身份变化 /
   * 提交成功 / 收到额度错误时调用。旧查询结果不得再写回额度。
   */
  invalidateQuotaRefresh() {
    this.quotaSeq++, this.cancelQuotaTimer();
  }
  /**
   * 统一调度额度查询（同一时刻最多一个定时器）：
   * - 未登录 / 面板未打开 / 应用不在前台 → 不排程；
   * - 刷新失败 → 30 秒后重试（不对过期 resetAt 立即循环请求）；
   * - 额度用尽 → 每 30 秒一次，并与 resetAt 合并取更早者
   *   （后台调高额度后最多 30 秒即可恢复，无需关闭重开面板）；
   * - 其它状态 → 只在服务端 resetAt 排单次查询，到期只向服务端取最新值。
   */
  scheduleQuotaRefresh() {
    if (this.cancelQuotaTimer(), !this.tokenValid() || !this.openState || typeof document < "u" && document.visibilityState !== "visible" || this.quotaInFlight) return;
    if (this.quotaRefreshFailed) {
      this.quotaTimer = setTimeout(() => {
        this.refreshQuota();
      }, Pt);
      return;
    }
    const t = this.quota;
    if (!t || t.unlimited === !0 || typeof t.remaining != "number" || typeof t.resetAt != "string")
      return;
    const e = Date.parse(t.resetAt), s = Number.isFinite(e) ? e - Date.now() : Number.NaN;
    let n;
    t.remaining <= 0 ? n = s > 0 && s < Pt ? s : Pt : s > 0 ? n = s : n = null, !(n === null || n <= 0) && (n = Math.min(n, Di), this.quotaTimer = setTimeout(() => {
      this.refreshQuota();
    }, n));
  }
  /**
   * 刷新额度与会话信息：打开面板、应用恢复前台、提交完成、额度用尽轮询时调用。
   * 不在本地擅自重置——跨过 resetAt 后由服务端返回新的 used / remaining。
   * 结果除校验身份与令牌外还校验查询序号：提交成功后的旧查询不得把次数加回。
   * 被旧查询 / 登录忙碌挡下时登记「待立即刷新」，结束后立即补发（T1-B）。
   */
  async refreshQuota() {
    const t = this.effectiveApiBase;
    if (!t || !this.tokenValid() || this.loginBusy || this.quotaInFlight) {
      t && this.tokenValid() && this.openState && (this.loginBusy || this.quotaInFlight) && (typeof document > "u" || document.visibilityState === "visible") && (this.quotaRefreshPending = !0);
      return;
    }
    const e = this.identityEpoch, s = this.accessToken, n = ++this.quotaSeq;
    this.quotaInFlight = !0;
    try {
      const r = await Te(t, s, { fetchFn: this.apiFetch });
      if (!this.quotaResultValid(n, e, s)) return;
      this.authUser = r.user, this.quota = r.quota ?? null, this.applyCapabilities(r.capabilities), this.quotaRefreshFailed = !1, this.syncUi();
    } catch (r) {
      if (!this.quotaResultValid(n, e, s)) return;
      if (r instanceof H && r.status === 401) {
        this.accessToken = null, this.tokenExpiresAt = 0, this.authRequired = !0, this.authUser = null, this.quota = null, this.quotaRefreshFailed = !1, this.quotaRefreshPending = !1, this.invalidateQuotaRefresh(), this.renderStatus("登录已过期，请重新登录。"), this.syncUi(), this.clearHostSession();
        return;
      }
      this.quotaRefreshFailed = !0;
    } finally {
      this.quotaInFlight = !1, this.consumeQuotaRefreshIntent();
    }
  }
  /**
   * 消费「待立即刷新」意图（T1-B）：优先于定时规则——旧查询结束 / 忙碌结束
   * 后条件仍满足时立即补发一次并清除标记；否则按现有 30 秒 / resetAt 规则
   * 排定时器（不会紧密循环，也不会因一次跳过而永久停摆）。
   */
  consumeQuotaRefreshIntent() {
    if (this.quotaRefreshPending && this.tokenValid() && this.openState && !this.loginBusy && !this.quotaInFlight && (typeof document > "u" || document.visibilityState === "visible")) {
      this.quotaRefreshPending = !1, this.refreshQuota();
      return;
    }
    this.scheduleQuotaRefresh();
  }
  /** 额度查询结果是否仍然有效：序号 + 身份世代 + 令牌三者都要匹配。 */
  quotaResultValid(t, e, s) {
    return t === this.quotaSeq && e === this.identityEpoch && s === this.accessToken;
  }
  // ---------- v12 服务端能力 ----------
  /**
   * 应用服务端能力声明（登录 / 会话响应附带 `capabilities`）：
   * - `images: N` → 允许多图（上限取 min(N, 5)）；
   * - 字段缺失（旧服务端）→ 单图模式 + 升级提示；
   * - 字段存在且为 0/负值 → 同样按单图处理。
   * 会话响应携带 `capabilities` 对象即视为「已确认」（缺失 images 也确认）。
   */
  applyCapabilities(t) {
    const e = pe(t);
    e !== void 0 && (this.capabilitiesKnown = !0, this.maxImages = Math.max(1, Math.min(J, e.images ?? 1)));
  }
  /**
   * 能力校准顺序：认证响应（login / session 的 capabilities）优先；
   * 能力仍未知且需要判断（提交多图）时才探测 `GET /api/features`
   * （无需登录，跨源放行）。探测成功即确认（响应缺 images → 单图模式）；
   * 探测失败保持未确认——乐观放行多图，由服务端裁决。
   * `GET /api/features` 同时以 api.ts 导出供宿主主动探测。
   */
  async probeFeatures() {
    const t = this.effectiveApiBase;
    if (!t || this.capabilitiesKnown || this.featuresProbing) return;
    const e = this.identityEpoch;
    this.featuresProbing = !0;
    try {
      const s = await Pe(t, { fetchFn: this.apiFetch });
      if (e !== this.identityEpoch) return;
      this.applyCapabilities(s);
    } catch {
    } finally {
      e === this.identityEpoch && (this.featuresProbing = !1);
    }
  }
  /** 公开 API：展开面板内登录表单（保留旧签名的返回值）。 */
  startLogin() {
    return this.beginLogin(!1), "";
  }
  /**
   * 宿主注入会话：与面板内登录等价，但凭据由宿主提供——例如同源管理后台
   * 以自身 Cookie 会话调 `POST /api/auth/handshake` 换取的握手令牌。
   * 令牌同样仅存内存；宿主负责到期前续注（重新调用本方法）或在自身
   * 会话结束时调用 dropSession()。注入后组件自动拉取会话身份与额度。
   * 注入非法参数（空令牌 / 不可解析的过期时刻）按 no-op 处理。
   */
  adoptSession(t) {
    const e = typeof t?.accessToken == "string" ? t.accessToken : "", s = t?.expiresAt, n = typeof s == "number" ? s : Date.parse(typeof s == "string" ? s : "");
    !e || !Number.isFinite(n) || (this.identityEpoch++, this.accessToken = e, this.tokenExpiresAt = n, this.authRequired = !1, this.loginVisible = !1, this.loginBusy = !1, this.loginError = "", this.quotaRefreshFailed = !1, this.capabilitiesKnown = !1, this.maxImages = J, this._diagnostics?.clear(), this.renderStatus("已登录。"), this.syncUi(), this.refreshQuota(), this.startSummaryPolling());
  }
  /**
   * 宿主清除注入的会话（如宿主自身退出登录）：丢弃令牌与身份，回到
   * 需要登录态；草稿 / 截图 / 日志保留（与令牌被撤销同语义）。
   */
  dropSession() {
    this.clearHostSession(), this.accessToken !== null && (this.identityEpoch++, this.accessToken = null, this.tokenExpiresAt = 0, this._diagnostics?.clear(), this.authUser = null, this.quota = null, this.quotaRefreshFailed = !1, this.quotaRefreshPending = !1, this.invalidateQuotaRefresh(), this.stopSummaryPolling(), this.stopDialoguePolling(), this.unreadCount = 0, this.syncUnreadBadge(), this.authRequired = !0, this.renderStatus("需要登录"), this.syncUi());
  }
  resetToCompose() {
    this.stopPolling(), this.phase = "idle", this.lastFeedbackId = null, this.lastRecord = null, this.lastErrorSummary = null, this.waitingNotice = null, this.idempotencyKey = null, this.submitSnapshot = null, this.unconfirmedRequest = null, this.textarea.value = "", this.clearDraft(), this.renderStatus(""), this.syncUi(), this.textarea.focus();
  }
  // ---------- 渲染与 UI 同步 ----------
  renderStatus(t) {
    this.statusRegion.textContent = t;
  }
  syncUi() {
    const t = gt(this.textarea.value);
    this.counter.textContent = `${t}/${Y}`, this.counter.classList.toggle("is-over", t > Y);
    const e = this.tokenValid(), s = this.phase === "submitting" || this.polling, n = this.submitSnapshot, r = n !== null && n.unknownOutcome && n.draftVersion === this.draft.version && n.text === this.textarea.value && Ut(n.images, this.draft.images.map(Ot)) && zt(n.logs, this.draft.logs), a = e && this.quota !== null && this.quota.unlimited !== !0 && this.quota.remaining <= 0 && !r;
    if (e && this.quota ? (this.quotaInfo.hidden = !1, this.quotaInfo.textContent = this.quota.unlimited === !0 ? "不限次数" : `今日剩余 ${this.quota.remaining} 次`) : (this.quotaInfo.hidden = !0, this.quotaInfo.textContent = ""), a && this.quota) {
      const o = new Date(this.quota.resetAt);
      this.quotaBlockedInfo.hidden = !1, this.quotaBlockedInfo.textContent = Number.isFinite(o.getTime()) ? `今日提交次数已用完，下次可提交时间：${o.toLocaleString()}` : "今日提交次数已用完，请在额度刷新后重试";
    } else
      this.quotaBlockedInfo.hidden = !0, this.quotaBlockedInfo.textContent = "";
    if (this.loginPanel.hidden = !this.loginVisible, this.loginUsername.disabled = this.loginBusy, this.loginPassword.disabled = this.loginBusy, this.loginConfirmBtn.disabled = this.loginBusy, this.loginCancelBtn.disabled = !1, this.loginConfirmBtn.textContent = this.loginBusy ? "登录中…" : "登录并提交", this.loginErrorEl.hidden = this.loginError === "", this.loginErrorEl.textContent = this.loginError, this.syncShotUi(), this.syncLogsUi(), this.syncSettingsUi(), this.tabComposeBtn && this.tabComposeBtn.classList.toggle("active", this.activeTab === "compose"), this.tabHistoryBtn && this.tabHistoryBtn.classList.toggle("active", this.activeTab === "history"), this.syncUnreadBadge(), this.textarea.disabled = this.phase === "submitting", e ? this.phase === "submitting" ? (this.submitBtn.className = "fb-submit", this.submitBtn.textContent = "提交中…", this.submitBtn.disabled = !0) : this.phase === "archived" ? (this.submitBtn.className = "fb-submit", this.submitBtn.textContent = "已归档", this.submitBtn.disabled = !0) : this.phase === "failed" && this.idempotencyKey !== null && !this.lastRecord ? (this.submitBtn.className = "fb-submit", this.submitBtn.textContent = "重试提交", this.submitBtn.disabled = s || t === 0 || t > Y || a) : (this.submitBtn.className = "fb-submit", this.submitBtn.textContent = "提交", this.submitBtn.disabled = s || t === 0 || t > Y || this.phase === "tracking" || a) : (this.submitBtn.className = "fb-submit fb-login", this.submitBtn.textContent = "登录并提交", this.submitBtn.disabled = t === 0 || t > Y || s, !this.statusRegion.textContent && t > 0 && this.renderStatus("需要登录")), this.errorRegion.textContent = "", this.errorRegion.hidden = !0, this.phase === "archived") {
      this.errorRegion.hidden = !1;
      const o = l("div", "fb-status-card is-success");
      o.append(l("p", void 0, "反馈已归档。感谢你的支持！"));
      const h = l("div", "fb-card-actions");
      if (this.lastRecord?.kaneoUrl) {
        const c = l("a", "fb-task-link", "查看任务"), u = this.lastRecord.kaneoUrl;
        c.href = u, c.target = "_blank", c.rel = "noopener", c.setAttribute("aria-label", "打开 Kaneo 归档任务"), c.addEventListener("click", (f) => {
          this._hostBridge?.onOpenExternal && (f.preventDefault(), this.openExternal(u));
        }), h.append(c);
      }
      const d = l("button", "fb-btn-secondary", "再记一条");
      d.type = "button", d.addEventListener("click", () => this.resetToCompose()), h.append(d), o.append(h), this.errorRegion.append(o);
    } else if (this.phase === "failed" && this.lastRecord) {
      this.errorRegion.hidden = !1;
      const o = l("div", "fb-status-card is-warn");
      o.append(l("p", void 0, "原话已保存，后台整理未完成，可在管理页处理。请勿重复提交。")), this.lastErrorSummary && o.append(l("p", "fb-header-meta", `详情：${this.lastErrorSummary}`));
      const h = this.lastFeedbackId ?? this.lastRecord.id;
      o.append(
        l("p", "fb-header-meta", `反馈标识：${h}（可在管理页据此找回原话与截图）`)
      );
      const d = l("div", "fb-card-actions"), c = l(
        "button",
        "fb-btn-secondary fb-refresh-btn",
        this.refreshing ? "刷新中…" : "刷新状态"
      );
      c.type = "button", c.setAttribute("aria-label", "刷新反馈处理状态"), c.disabled = this.refreshing, c.addEventListener("click", () => {
        this.refreshLastRecord();
      });
      const u = l("button", "fb-btn-secondary fb-copy-id-btn", "复制标识");
      u.type = "button", u.setAttribute("aria-label", "复制反馈标识"), u.addEventListener("click", () => this.copyFeedbackId());
      const f = l("button", "fb-btn-secondary", "再记一条");
      f.type = "button", f.addEventListener("click", () => this.resetToCompose()), d.append(c, u, f), o.append(d), this.errorRegion.append(o);
    } else if (this.phase === "needs_review") {
      this.errorRegion.hidden = !1;
      const o = l("div", "fb-status-card is-warn");
      o.append(l("p", void 0, "归档结果待确认。原话已保存，将在管理页人工复核。"));
      const h = l("div", "fb-card-actions"), d = l("button", "fb-btn-secondary", "再记一条");
      d.type = "button", d.addEventListener("click", () => this.resetToCompose()), h.append(d), o.append(h), this.errorRegion.append(o);
    } else if (this.phase === "quota") {
      this.errorRegion.hidden = !1;
      const o = l("div", "fb-status-card is-warn");
      if (o.append(l("p", void 0, "今日提交次数已用完。文字与截图已保留，额度刷新后可直接再提交。")), this.quota) {
        const h = new Date(this.quota.resetAt);
        Number.isFinite(h.getTime()) && o.append(l("p", "fb-header-meta", `下次可提交时间：${h.toLocaleString()}`));
      }
      this.errorRegion.append(o);
    } else if (this.phase === "failed" && !this.lastRecord) {
      this.errorRegion.hidden = !1;
      const o = l("div", "fb-status-card is-error");
      if (o.append(l("p", "fb-error-summary", "尚未确认保存，请检查网络后重试。")), this.unconfirmedRequest) {
        const c = this.unconfirmedRequest;
        o.append(
          l(
            "p",
            "fb-header-meta",
            `已保留一条结果未知的原请求（标识 ${c.key.slice(0, 8)}…，截图时间 ${c.capturedAt ?? "未知"}），请先核对其是否已被服务端接收，避免重复提交。`
          )
        );
      }
      this.lastErrorSummary && o.append(l("p", "fb-header-meta", this.lastErrorSummary));
      const h = l("div", "fb-card-actions"), d = l("button", "fb-btn-secondary fb-retry fb-retry-btn", "重试提交");
      d.type = "button", d.addEventListener("click", () => {
        this.submit();
      }), h.append(d), o.append(h), this.errorRegion.append(o);
    } else if (this.phase === "tracking") {
      this.errorRegion.hidden = !1;
      const o = l("div", "fb-status-card is-warn");
      if (this.waitingNotice !== null) {
        o.append(l("p", void 0, this.waitingNotice)), o.append(l("p", "fb-header-meta", "无需重复提交；管理员处理后点「刷新状态」即可查看结果。"));
        const h = l("div", "fb-card-actions"), d = l("button", "fb-btn-secondary fb-refresh-btn", this.refreshing ? "刷新中…" : "刷新状态");
        d.type = "button", d.setAttribute("aria-label", "刷新反馈处理状态"), d.disabled = this.refreshing, d.addEventListener("click", () => {
          this.refreshLastRecord();
        });
        const c = l("button", "fb-btn-secondary", "再记一条");
        c.type = "button", c.addEventListener("click", () => this.resetToCompose()), h.append(d, c), o.append(h);
      } else
        o.append(l("p", void 0, "已保存，正在整理。您可以关闭面板，我们将继续在后台处理。"));
      this.errorRegion.append(o);
    }
  }
  // ---------- 键盘与焦点 ----------
  onKeydown(t) {
    if (!this.editorHandle && !this.regionSession && this.handledKey !== t) {
      if (this.handledKey = t, (t.metaKey || t.ctrlKey) && t.key === "Enter" && !t.isComposing && t.keyCode !== 229 && !this.submitBtn.disabled) {
        t.preventDefault(), this.onPrimaryAction();
        return;
      }
      if (t.key === "Escape") {
        if (this.logPreviewModal && this.logPreviewModal.classList.contains("is-open")) {
          t.preventDefault(), this.closeLogPreview();
          return;
        }
        if (this.zoomModal && this.zoomModal.classList.contains("is-open")) {
          t.preventDefault(), this.closeZoomModal();
          return;
        }
        if (this.settingsOpen) {
          t.preventDefault(), this.closeServerSettings();
          return;
        }
        if (this.openState && (this.root.activeElement !== null || this.contains(document.activeElement) || this === document.activeElement)) {
          t.preventDefault(), this.close();
          return;
        }
      }
      if (t.key === "Tab" && this.openState && this.isMobile()) {
        const e = this.focusables();
        if (e.length === 0) return;
        const s = this.root.activeElement, n = s ? e.indexOf(s) : -1;
        if (n === -1) {
          t.preventDefault(), e[0]?.focus();
          return;
        }
        t.shiftKey && n === 0 ? (t.preventDefault(), e[e.length - 1]?.focus()) : !t.shiftKey && n === e.length - 1 && (t.preventDefault(), e[0]?.focus());
      }
    }
  }
  focusables() {
    const t = this.panel.querySelectorAll(
      'button:not([disabled]), textarea:not([disabled]), input:not([disabled]), a[href], [tabindex="0"]'
    );
    return Array.from(t).filter((e) => e.closest("[hidden]") === null);
  }
}
const _t = "feedback-widget";
function Se(i = _t) {
  return typeof customElements < "u" && !customElements.get(i) && customElements.define(i, Bt), Bt;
}
Se();
function Ki(i = {}) {
  Se();
  let t = document.querySelector(_t);
  return t ? (i.apiBase && (t.apiBase = i.apiBase), i.appId && (t.appId = i.appId), i.appVersion !== void 0 && (t.appVersion = i.appVersion), i.pageLabel !== void 0 && (t.pageLabel = i.pageLabel), i.side && (t.side = i.side), i.theme && (t.theme = i.theme), i.showLauncher !== void 0 && (t.showLauncher = i.showLauncher), i.launcherBottom && (t.launcherBottom = i.launcherBottom), i.launcherMode && (t.launcherMode = i.launcherMode), i.captureMode && (t.captureMode = i.captureMode), i.logProvider !== void 0 && (t.logProvider = i.logProvider), i.diagnosticsRecorder !== void 0 && (t.diagnosticsRecorder = i.diagnosticsRecorder), i.sessionStore !== void 0 && (t.sessionStore = i.sessionStore), i.hostBridge !== void 0 && (t.hostBridge = i.hostBridge)) : (t = document.createElement(_t), i.apiBase && (t.apiBase = i.apiBase), i.appId && (t.appId = i.appId), i.appVersion && (t.appVersion = i.appVersion), i.pageLabel && (t.pageLabel = i.pageLabel), i.side && (t.side = i.side), i.theme && (t.theme = i.theme), i.showLauncher !== void 0 && (t.showLauncher = i.showLauncher), i.launcherBottom && (t.launcherBottom = i.launcherBottom), i.launcherMode && (t.launcherMode = i.launcherMode), i.captureMode && (t.captureMode = i.captureMode), i.logProvider !== void 0 && (t.logProvider = i.logProvider), i.diagnosticsRecorder !== void 0 && (t.diagnosticsRecorder = i.diagnosticsRecorder), i.sessionStore !== void 0 && (t.sessionStore = i.sessionStore), i.hostBridge !== void 0 && (t.hostBridge = i.hostBridge), document.body.appendChild(t)), t.open(), t;
}
export {
  _t as FEEDBACK_ELEMENT_TAG,
  ji as FeedbackDiagnosticsRecorder,
  Wi as FeedbackErrorCapture,
  Bt as FeedbackWidget,
  Se as defineFeedbackWidget,
  nt as normalizeServerBase,
  Ki as openFeedback,
  Ai as wrapFetch
};
