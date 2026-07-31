import { Link } from 'react-router-dom';

export default function Brand({ version = 'v1.0.0' }) {
  return (
    <Link to="/" className="flex items-center gap-2 select-none">
      <span className="text-xl font-extrabold tracking-tight text-ink">
        PU<span className="text-maroon">KAR</span>
      </span>
      <span className="font-mono text-[11px] text-gray-400">{version}</span>
    </Link>
  );
}
