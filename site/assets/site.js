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
})();
