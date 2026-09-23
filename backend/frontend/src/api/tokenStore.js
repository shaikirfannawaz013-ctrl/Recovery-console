const ACCESS = 'prp.accessToken';
const REFRESH = 'prp.refreshToken';
const USER = 'prp.user';

export const tokenStore = {
  access: () => localStorage.getItem(ACCESS),
  refresh: () => localStorage.getItem(REFRESH),
  user: () => {
    try { return JSON.parse(localStorage.getItem(USER)); } catch { return null; }
  },
  save({ accessToken, refreshToken, user }) {
    if (accessToken) localStorage.setItem(ACCESS, accessToken);
    if (refreshToken) localStorage.setItem(REFRESH, refreshToken);
    if (user) localStorage.setItem(USER, JSON.stringify(user));
  },
  clear() {
    [ACCESS, REFRESH, USER].forEach((k) => localStorage.removeItem(k));
  },
};
