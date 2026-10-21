/* Oakfield Clinic Portal - progressive enhancement (no inline scripts in markup). */
(function () {
  "use strict";

  // Mobile sidebar toggle
  document.addEventListener("click", function (e) {
    var toggle = e.target.closest(".menu-toggle");
    if (toggle) {
      var sidebar = document.querySelector(".sidebar");
      var backdrop = document.querySelector(".sidebar-backdrop");
      sidebar.classList.toggle("open");
      if (backdrop) backdrop.classList.toggle("open");
      return;
    }
    if (e.target.closest(".sidebar-backdrop")) {
      document.querySelector(".sidebar").classList.remove("open");
      document.querySelector(".sidebar-backdrop").classList.remove("open");
    }
  });

  // Confirmable actions (links/buttons with data-confirm)
  document.addEventListener("submit", function (e) {
    var form = e.target;
    if (form.dataset && form.dataset.confirm) {
      if (!window.confirm(form.dataset.confirm)) {
        e.preventDefault();
        e.stopPropagation();
      }
    }
  });
  document.addEventListener("click", function (e) {
    var el = e.target.closest("[data-confirm]");
    if (el && el.tagName !== "FORM") {
      if (!window.confirm(el.getAttribute("data-confirm"))) {
        e.preventDefault();
        e.stopPropagation();
      }
    }
  });

  // Auto-dismiss flash messages after 7s
  document.querySelectorAll(".alert").forEach(function (alert) {
    if (!alert.classList.contains("alert--error")) {
      setTimeout(function () {
        alert.style.transition = "opacity 0.4s ease";
        alert.style.opacity = "0";
        setTimeout(function () { alert.remove(); }, 450);
      }, 7000);
    }
  });

  // Character counters for textareas with data-maxlength
  document.querySelectorAll("textarea[data-maxlength]").forEach(function (area) {
    var max = parseInt(area.getAttribute("data-maxlength"), 10);
    var counter = document.createElement("div");
    counter.className = "help";
    counter.textContent = area.value.length + " / " + max;
    area.insertAdjacentElement("afterend", counter);
    area.addEventListener("input", function () {
      counter.textContent = area.value.length + " / " + max;
      counter.style.color = area.value.length > max ? "#9E2424" : "";
    });
  });

  // Keyboard: tabs stay in order; buttons act on Enter/Space natively.
})();
