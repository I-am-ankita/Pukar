import React from "react";

export default function App() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 px-6 py-8 font-sans">
      <div className="max-w-3xl mx-auto">
        <h1 className="text-3xl font-semibold mb-2">HELLO application demo</h1>
        <p className="text-slate-600 mb-6">Welcome to the demo app (Vite + React)</p>
        <section className="mt-6 rounded-2xl border border-slate-300 bg-white p-6 shadow-sm">
          <h2 className="text-2xl font-medium mb-3">Pukar</h2>
          <p className="text-slate-600 mb-4">This is a demo template for the Civic Complaint Platform.</p>
          <ul className="list-disc list-inside space-y-2 text-slate-700">
            <li>Submit a complaint</li>
            <li>Track complaint status</li>
            <li>Provide feedback</li>
          </ul>
        </section>
      </div>
    </div>
  );
}
