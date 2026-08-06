import axios from 'axios';

const client = axios.create({ baseURL: '/api/v1' });

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('pukar_access');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

client.interceptors.response.use(
  (res) => res,
  async (err) => {
    const original = err.config;
    if (err.response?.status === 401 && !original._retry) {
      const refresh = localStorage.getItem('pukar_refresh');
      if (refresh) {
        original._retry = true;
        try {
          const r = await axios.post('/api/v1/auth/refresh', { refreshToken: refresh });
          const data = r.data?.data;
          if (data?.accessToken) {
            localStorage.setItem('pukar_access', data.accessToken);
            localStorage.setItem('pukar_refresh', data.refreshToken);
            original.headers.Authorization = `Bearer ${data.accessToken}`;
            return client(original);
          }
        } catch (_) {
          localStorage.removeItem('pukar_access');
          localStorage.removeItem('pukar_refresh');
        }
      }
    }
    return Promise.reject(err);
  }
);

// unwrap {success,data,error}
export const unwrap = (p) => p.then((r) => r.data?.data).catch((e) => {
  const msg = e.response?.data?.error?.message || e.message || 'Request failed';
  throw new Error(msg);
});

export default client;
