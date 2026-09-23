import { Link } from 'react-router-dom';
export default function NotFound() {
  return (
    <div className="state state--empty">
      <strong>This page doesn’t exist.</strong>
      <Link to="/" className="link">Go to the overview</Link>
    </div>
  );
}
