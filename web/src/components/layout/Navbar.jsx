import React, { useRef, useEffect } from 'react';
import { User, LogOut } from 'lucide-react';

export default function Navbar({
  currentUser,
  showDropdown,
  setShowDropdown,
  onOpenProfile,
  onLogout,
}) {
  const dropdownRef = useRef(null);
  const userInitial = (currentUser?.fullName || currentUser?.username || 'U').charAt(0).toUpperCase();

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [setShowDropdown]);

  return (
    <header className="top-navbar">
      <div className="nav-inner">
        <div className="brand-wrapper">
          <div className="brand-text-single">Mini Digital Wallet</div>
        </div>

        <div className="user-dropdown-area" ref={dropdownRef}>
          <button
            className="user-avatar-btn"
            onClick={() => setShowDropdown(!showDropdown)}
            title="Account Menu"
            aria-label="User profile menu"
          >
            {userInitial}
          </button>

          {showDropdown && (
            <div className="dropdown-menu">
              <div className="dropdown-user-header">
                <div className="dropdown-user-name">{currentUser?.fullName || currentUser?.username}</div>
                <div className="dropdown-user-email">{currentUser?.email || currentUser?.username}</div>
              </div>

              <button
                className="dropdown-item"
                onClick={() => {
                  setShowDropdown(false);
                  onOpenProfile();
                }}
              >
                <User size={15} color="var(--blue-primary)" />
                Profile
              </button>

              <button className="dropdown-item danger" onClick={onLogout}>
                <LogOut size={15} />
                Log Out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
