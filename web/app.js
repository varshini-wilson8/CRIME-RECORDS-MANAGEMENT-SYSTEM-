/**
 * CRMS 2.0 Global Application Script
 * Provides global header user identity widget, RBAC status dropdown,
 * modal-based secure sign out workflow, and sidebar navigation enhancement.
 * Encapsulated in an IIFE to eliminate global namespace collisions.
 */
(function () {
  'use strict';

  function esc(value) {
    return String(value ?? '').replace(/[&<>"']/g, function (c) {
      return {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
      }[c];
    });
  }

  async function initGlobalHeaderAndAuth() {
    let me = null;
    try {
      const res = await fetch('/api/me');
      if (res.status === 401) {
        if (!window.location.pathname.startsWith('/login')) {
          window.location.replace('/login');
        }
        return;
      }
      if (res.ok) {
        me = await res.json();
      }
    } catch (err) {
      // Offline / network failure handling
    }

    if (!me || !me.authenticated) return;

    // 1. Locate or create top navigation header container
    let pageTop = document.querySelector('.page-top') || document.querySelector('.top') || document.querySelector('.workspace-top');
    if (!pageTop) {
      const mainContent = document.querySelector('.content') || document.querySelector('.cc') || document.querySelector('main') || document.body;
      pageTop = document.createElement('div');
      pageTop.className = 'page-top';
      pageTop.style.cssText = 'display:flex; justify-content:space-between; align-items:center; margin-bottom:14px;';
      mainContent.insertBefore(pageTop, mainContent.firstChild);
    }

    // Remove existing widget if re-rendered
    document.getElementById('crmsUserWidget')?.remove();

    const userWidget = document.createElement('div');
    userWidget.className = 'crms-user-widget';
    userWidget.id = 'crmsUserWidget';

    const avatarChar = (me.displayName || me.username || 'U').trim().substring(0, 1).toUpperCase();
    const displayName = me.displayName || me.username;
    const roleTitle = me.roleTitle || me.role;

    userWidget.innerHTML = `
      <button type="button" class="crms-user-btn" id="crmsUserBtn" aria-expanded="false" title="Account Menu">
        <div class="crms-user-avatar">${esc(avatarChar)}</div>
        <div class="crms-user-info">
          <span class="crms-user-name">${esc(displayName)}</span>
          <span class="crms-user-role">${esc(roleTitle)}</span>
        </div>
        <span class="crms-dropdown-caret">&#9660;</span>
      </button>
      <div class="crms-dropdown-menu" id="crmsDropdownMenu">
        <div class="crms-dropdown-header">
          <strong>${esc(displayName)}</strong>
          <span class="role-title">${esc(roleTitle)} (${esc(me.username)})</span>
          <span class="crms-dropdown-status">Active Session</span>
        </div>
        <a href="/account" class="crms-dropdown-item">&#128100; My Account</a>
        <button type="button" class="crms-dropdown-item logout" id="crmsDropLogout">&#128682; Sign Out</button>
      </div>
    `;

    // Ensure right-aligned placement alongside existing action buttons
    let rightContainer = pageTop.querySelector('.page-top-actions') || pageTop.querySelector('.actions');
    if (!rightContainer) {
      const nonTitleChildren = Array.from(pageTop.children).filter(function (c) {
        return !c.classList.contains('eyebrow') &&
               !c.classList.contains('back-link') &&
               c.tagName !== 'H1' &&
               c.tagName !== 'P' &&
               !c.classList.contains('crms-user-widget');
      });

      rightContainer = document.createElement('div');
      rightContainer.className = 'page-top-actions';
      rightContainer.style.cssText = 'display:flex; align-items:center; gap:12px; margin-left:auto;';

      nonTitleChildren.forEach(function (c) {
        rightContainer.appendChild(c);
      });
      pageTop.appendChild(rightContainer);
    }
    rightContainer.appendChild(userWidget);

    // 2. User Dropdown Toggle
    const userBtn = document.getElementById('crmsUserBtn');
    const dropdownMenu = document.getElementById('crmsDropdownMenu');

    userBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      const isOpen = dropdownMenu.classList.toggle('show');
      userBtn.setAttribute('aria-expanded', String(isOpen));
    });

    document.addEventListener('click', function (e) {
      if (!userWidget.contains(e.target)) {
        dropdownMenu.classList.remove('show');
        userBtn.setAttribute('aria-expanded', 'false');
      }
    });

    // 3. Inject Sign Out Confirmation Modal if not present
    if (!document.getElementById('crmsSignOutModal')) {
      const modalHtml = `
        <div class="crms-modal-overlay" id="crmsSignOutModal" role="dialog" aria-modal="true" aria-labelledby="crmsModalTitle">
          <div class="crms-modal-card">
            <h3 class="crms-modal-title" id="crmsModalTitle">Sign Out Confirmation</h3>
            <p class="crms-modal-body">Are you sure you want to sign out? Your active authenticated session will be terminated on the server.</p>
            <div class="crms-modal-actions">
              <button type="button" class="crms-modal-btn cancel" id="crmsModalCancel">Cancel</button>
              <button type="button" class="crms-modal-btn confirm" id="crmsModalConfirm">Sign Out</button>
            </div>
          </div>
        </div>
      `;
      document.body.insertAdjacentHTML('beforeend', modalHtml);

      document.getElementById('crmsModalCancel').addEventListener('click', function () {
        document.getElementById('crmsSignOutModal').classList.remove('active');
      });

      document.getElementById('crmsModalConfirm').addEventListener('click', async function () {
        try {
          await fetch('/api/logout', {
            method: 'POST',
            headers: { 'Accept': 'application/json' }
          });
        } catch (e) {
          // Ignore network errors on logout redirect
        }
        window.location.replace('/login?loggedOut=true');
      });
    }

    window.showSignOutModal = function () {
      if (dropdownMenu) dropdownMenu.classList.remove('show');
      if (userBtn) userBtn.setAttribute('aria-expanded', 'false');
      const modal = document.getElementById('crmsSignOutModal');
      if (modal) modal.classList.add('active');
    };

    document.getElementById('crmsDropLogout')?.addEventListener('click', window.showSignOutModal);

    // 4. Update sidebar with Crime Prediction & My Account links if sidebar exists
    const sidebarNav = document.querySelector('.sidebar .nav') || document.querySelector('nav.nav');
    if (sidebarNav) {
      if (!sidebarNav.querySelector('a[href="/crime-prediction"]')) {
        const cpLink = document.createElement('a');
        cpLink.href = '/crime-prediction';
        cpLink.textContent = 'Crime prediction';
        if (window.location.pathname === '/crime-prediction') {
          cpLink.className = 'active';
        }
        const accExisting = sidebarNav.querySelector('a[href="/account"]');
        if (accExisting) {
          sidebarNav.insertBefore(cpLink, accExisting);
        } else {
          sidebarNav.appendChild(cpLink);
        }
      }
      if (!sidebarNav.querySelector('a[href="/account"]')) {
        const accLink = document.createElement('a');
        accLink.href = '/account';
        accLink.textContent = 'My Account';
        if (window.location.pathname === '/account') {
          accLink.className = 'active';
        }
        sidebarNav.appendChild(accLink);
      }
    }
  }

  // Keyboard accessibility: ESC closes modal and dropdown
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' || e.key === 'Esc') {
      const modal = document.getElementById('crmsSignOutModal');
      if (modal && modal.classList.contains('active')) {
        modal.classList.remove('active');
      }
      const dropdown = document.getElementById('crmsDropdownMenu');
      if (dropdown && dropdown.classList.contains('show')) {
        dropdown.classList.remove('show');
        document.getElementById('crmsUserBtn')?.setAttribute('aria-expanded', 'false');
      }
    }
  });

  window.initGlobalHeaderAndAuth = initGlobalHeaderAndAuth;

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initGlobalHeaderAndAuth);
  } else {
    initGlobalHeaderAndAuth();
  }
})();