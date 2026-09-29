/* BetterAIChat2 site — small, dependency-free interactions */
(function () {
  "use strict";

  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------- sticky nav shadow ---------- */
  var nav = document.getElementById("nav");
  function onScroll() { if (nav) nav.classList.toggle("scrolled", window.scrollY > 12); }
  onScroll();
  window.addEventListener("scroll", onScroll, { passive: true });

  /* ---------- GitHub star count ---------- */
  var starEl = document.getElementById("star-count");
  if (starEl) {
    fetch("https://api.github.com/repos/Verlintas/NovaBAIC", {
      headers: { Accept: "application/vnd.github+json" }
    })
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (data) {
        if (!data || typeof data.stargazers_count !== "number") return;
        var n = data.stargazers_count;
        // Keep the plain "GitHub" label when there is nothing to brag about.
        if (n < 1) return;
        starEl.textContent = n >= 1000 ? (n / 1000).toFixed(1).replace(/\.0$/, "") + "k" : String(n);
      })
      .catch(function () { /* keep the fallback label */ });
  }

  /* ---------- scroll reveal ---------- */
  var revealEls = document.querySelectorAll(".reveal");
  if ("IntersectionObserver" in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add("in");
          io.unobserve(entry.target);
        }
      });
    }, { rootMargin: "0px 0px -8% 0px", threshold: 0.05 });
    revealEls.forEach(function (el) { io.observe(el); });
  } else {
    revealEls.forEach(function (el) { el.classList.add("in"); });
  }

  /* ---------- feature tabs ---------- */
  var tabs = Array.prototype.slice.call(document.querySelectorAll(".tab"));
  var panels = Array.prototype.slice.call(document.querySelectorAll(".panel"));
  tabs.forEach(function (tab) {
    tab.addEventListener("click", function () {
      var id = tab.getAttribute("data-tab");
      tabs.forEach(function (t) {
        var active = t === tab;
        t.classList.toggle("is-active", active);
        t.setAttribute("aria-selected", active ? "true" : "false");
      });
      panels.forEach(function (p) {
        p.classList.toggle("is-active", p.getAttribute("data-panel") === id);
      });
    });
  });

  /* ---------- hero demo timeline ---------- */
  var demo = {
    msg: document.querySelector(".d-msg"),
    think: document.querySelector(".d-think"),
    thinkText: document.querySelector(".d-think-text"),
    answer: document.querySelector(".d-answer"),
    lines: Array.prototype.slice.call(document.querySelectorAll(".d-answer > *")),
    replay: document.getElementById("demo-replay")
  };
  var THINK_TEXT = "The user wants a weekend trip plan. Keep it scannable: a table, a checklist, one practical tip.";
  var demoRunning = false;

  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }

  function typeInto(el, text, speed) {
    return new Promise(function (resolve) {
      var i = 0;
      el.textContent = "";
      (function tick() {
        if (!demoRunning) { resolve(); return; }
        if (i >= text.length) { resolve(); return; }
        el.textContent += text.charAt(i++);
        setTimeout(tick, speed);
      })();
    });
  }

  function resetDemo() {
    [demo.msg, demo.think, demo.answer].forEach(function (el) {
      if (el) el.classList.remove("show");
    });
    if (demo.think) demo.think.classList.remove("is-done");
    if (demo.thinkText) demo.thinkText.textContent = "";
    demo.lines.forEach(function (l) { l.classList.remove("show"); });
    if (demo.replay) demo.replay.classList.remove("show");
  }

  function finishDemoInstant() {
    if (demo.thinkText) demo.thinkText.textContent = THINK_TEXT;
    if (demo.think) demo.think.classList.add("is-done");
    [demo.msg, demo.think, demo.answer].forEach(function (el) {
      if (el) el.classList.add("show");
    });
    demo.lines.forEach(function (l) { l.classList.add("show"); });
    if (demo.replay) demo.replay.classList.add("show");
  }

  function playDemo() {
    if (demoRunning || !demo.msg) return;
    demoRunning = true;
    resetDemo();

    if (reduceMotion) {
      finishDemoInstant();
      demoRunning = false;
      return;
    }

    (async function () {
      await sleep(450);
      if (!demoRunning) return;
      demo.msg.classList.add("show");
      await sleep(650);
      if (!demoRunning) return;
      demo.think.classList.add("show");
      await typeInto(demo.thinkText, THINK_TEXT, 14);
      await sleep(320);
      demo.think.classList.add("is-done");
      await sleep(220);
      demo.answer.classList.add("show");
      for (var i = 0; i < demo.lines.length; i++) {
        demo.lines[i].classList.add("show");
        await sleep(170);
      }
      await sleep(150);
      demo.replay.classList.add("show");
      demoRunning = false;
    })();
  }

  if (demo.replay) {
    demo.replay.addEventListener("click", function () {
      if (demoRunning) return;
      playDemo();
    });
  }

  // Start once the phone scrolls into view (or immediately if reduced motion).
  var phone = document.querySelector(".phone");
  if (phone && "IntersectionObserver" in window && !reduceMotion) {
    var demoIO = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          demoIO.disconnect();
          playDemo();
        }
      });
    }, { threshold: 0.35 });
    demoIO.observe(phone);
  } else if (phone) {
    playDemo();
  }

  /* ---------- screenshot lightbox ---------- */
  var posters = Array.prototype.slice.call(document.querySelectorAll(".poster"));
  var lightbox = document.getElementById("lightbox");
  if (lightbox && posters.length) {
    var lbImg = document.getElementById("lb-img");
    var lbCaption = document.getElementById("lb-caption");
    var lbClose = lightbox.querySelector(".lb-close");
    var lbPrev = lightbox.querySelector(".lb-prev");
    var lbNext = lightbox.querySelector(".lb-next");
    var current = 0;
    var lastFocus = null;

    function renderLightbox() {
      var poster = posters[current];
      var img = poster.querySelector("img");
      lbImg.src = poster.getAttribute("data-full");
      lbImg.alt = img ? img.alt : "";
      lbCaption.textContent = poster.getAttribute("data-caption") || "";
    }

    function openLightbox(index) {
      current = (index + posters.length) % posters.length;
      lastFocus = document.activeElement;
      renderLightbox();
      lightbox.hidden = false;
      document.body.classList.add("no-scroll");
      lbClose.focus();
    }

    function closeLightbox() {
      lightbox.hidden = true;
      document.body.classList.remove("no-scroll");
      lbImg.removeAttribute("src");
      if (lastFocus && lastFocus.focus) lastFocus.focus();
    }

    posters.forEach(function (poster, index) {
      poster.addEventListener("click", function () { openLightbox(index); });
    });
    lbClose.addEventListener("click", closeLightbox);
    lbPrev.addEventListener("click", function () { openLightbox(current - 1); });
    lbNext.addEventListener("click", function () { openLightbox(current + 1); });
    lightbox.addEventListener("click", function (event) {
      if (event.target === lightbox) closeLightbox();
    });
    document.addEventListener("keydown", function (event) {
      if (lightbox.hidden) return;
      if (event.key === "Escape") closeLightbox();
      else if (event.key === "ArrowLeft") openLightbox(current - 1);
      else if (event.key === "ArrowRight") openLightbox(current + 1);
    });
  }
})();
