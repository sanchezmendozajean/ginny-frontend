// Selector de tema (los mismos 8 de la app) y resaltado del índice de la documentación.
(function () {
  var themes = [
    ["menta", "#0db98e"], ["cielo", "#37a8ec"], ["lila", "#a58bec"], ["fucsia", "#e141aa"],
    ["rosa", "#ec93a8"], ["lava", "#e8542f"], ["durazno", "#e57f4f"], ["smog", "#8d939a"],
  ];
  var root = document.documentElement;

  function apply(name) {
    if (name === "menta") root.removeAttribute("data-theme");
    else root.setAttribute("data-theme", name);
    document.querySelectorAll(".themes button").forEach(function (b) {
      b.setAttribute("aria-pressed", String(b.dataset.theme === name));
    });
  }

  var saved = "menta";
  try { saved = localStorage.getItem("ginny-theme") || "menta"; } catch (e) {}
  if (!themes.some(function (t) { return t[0] === saved; })) saved = "menta";

  var box = document.querySelector(".themes");
  if (box) {
    themes.forEach(function (t) {
      var b = document.createElement("button");
      b.type = "button";
      b.dataset.theme = t[0];
      b.style.setProperty("--c", t[1]);
      b.title = "Tema " + t[0].charAt(0).toUpperCase() + t[0].slice(1);
      b.setAttribute("aria-label", b.title);
      b.addEventListener("click", function () {
        apply(t[0]);
        try { localStorage.setItem("ginny-theme", t[0]); } catch (e) {}
      });
      box.appendChild(b);
    });
  }
  apply(saved);

  // Índice de la documentación: marca la sección visible.
  var links = Array.prototype.slice.call(document.querySelectorAll(".toc a[href^='#']"));
  if (links.length && "IntersectionObserver" in window) {
    var byId = {};
    links.forEach(function (a) { byId[a.getAttribute("href").slice(1)] = a; });
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting && byId[e.target.id]) {
          links.forEach(function (a) { a.classList.remove("on"); });
          byId[e.target.id].classList.add("on");
        }
      });
    }, { rootMargin: "-90px 0px -65% 0px" });
    Object.keys(byId).forEach(function (id) {
      var el = document.getElementById(id);
      if (el) io.observe(el);
    });
  }

  // ── Maquetas de la interfaz ──────────────────────────────────
  var GAMEPAD = "M33.9,36.2V50M27,43.1H40.8M6.8645,55.1451,4.3693,65.5324A13.5676,13.5676,0,0,0,14.783,81.8435a14.0721,14.0721,0,0,0,15.6975-7.2491l.5888-1.17a15.6772,15.6772,0,0,1,14.0381-8.521h9.7851a15.6773,15.6773,0,0,1,14.0383,8.521l.5888,1.17a14.0719,14.0719,0,0,0,15.6975,7.2491A13.5676,13.5676,0,0,0,95.6306,65.5324l-4.99-20.775C87.82,33.0141,86.409,27.1421,82.4084,23.2911a19.469,19.469,0,0,0-3.3373-2.5762C74.3068,17.8,68.1106,17.8,55.7187,17.8H44.2813c-12.3921,0-18.5883,0-23.3522,2.9149a19.45,19.45,0,0,0-3.3373,2.5762c-2.8823,2.7743-4.42,6.5974-6.1094,12.9089Zm57.5741-5.5725a4.6,4.6,0,1,1-4.6-4.6A4.6,4.6,0,0,1,64.4386,49.5726ZM78.24,38.4051a4.6,4.6,0,1,1-4.6-4.6A4.6,4.6,0,0,1,78.24,38.4051Z";
  var SHAPES = '<circle cx="90" cy="70" r="34"/><rect x="760" y="40" width="70" height="70" rx="6" transform="rotate(18 795 75)"/><polygon points="420,34 462,104 378,104"/><circle cx="640" cy="470" r="26"/><rect x="40" y="420" width="64" height="64" rx="6" transform="rotate(-14 72 452)"/><polygon points="880,400 930,486 830,486"/><circle cx="300" cy="500" r="18"/><rect x="540" y="22" width="40" height="40" rx="4" transform="rotate(30 560 42)"/><circle cx="905" cy="250" r="22"/><polygon points="170,250 200,300 140,300"/>';
  // Distribución tipo Nintendo (YX/BA): dónde está cada botón en el ícono del mando.
  var POS = { A: [78, 50], B: [50, 78], X: [50, 22], Y: [22, 50] };
  var ALL = [[78, 50], [50, 78], [50, 22], [22, 50]];

  document.querySelectorAll(".fb").forEach(function (el) {
    var on = POS[el.dataset.b] || POS.A;
    el.innerHTML = '<svg viewBox="0 0 100 100" aria-hidden="true">' + ALL.map(function (p) {
      var active = p[0] === on[0] && p[1] === on[1];
      return '<circle cx="' + p[0] + '" cy="' + p[1] + '" r="16"' + (active ? ' class="on"' : "") + "/>";
    }).join("") + "</svg>";
  });
  document.querySelectorAll(".m-gp").forEach(function (s) {
    s.setAttribute("viewBox", "0 0 100 100");
    s.innerHTML = '<path d="' + GAMEPAD + '"/>';
  });
  document.querySelectorAll(".m-bgshapes").forEach(function (s) {
    s.setAttribute("viewBox", "0 0 960 540");
    s.setAttribute("aria-hidden", "true");
    s.innerHTML = '<g fill="none" stroke="rgba(255,255,255,.6)" stroke-width="2">' + SHAPES + "</g>";
  });

  // Cada maqueta se dibuja a 960x540 y se escala al ancho de su marco.
  var frames = Array.prototype.slice.call(document.querySelectorAll(".mockframe"));
  function fit(frame) {
    var m = frame.querySelector(".mock");
    if (m) m.style.transform = "scale(" + (frame.clientWidth / 960) + ")";
  }
  frames.forEach(fit);
  if ("ResizeObserver" in window) {
    var ro = new ResizeObserver(function (list) { list.forEach(function (e) { fit(e.target); }); });
    frames.forEach(function (f) { ro.observe(f); });
  } else {
    window.addEventListener("resize", function () { frames.forEach(fit); });
  }
})();
