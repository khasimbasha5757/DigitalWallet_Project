/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        ink: {
          950: "#06131b",
          900: "#0d1f29",
          800: "#14303c"
        },
        mist: "#526072",
        coral: "#ff845f",
        gold: "#ffcb69",
        aqua: "#68e1c6"
      },
      boxShadow: {
        glow: "0 20px 60px rgba(6, 19, 27, 0.45)"
      },
      fontFamily: {
        display: ["Segoe UI", "sans-serif"],
        body: ["Segoe UI", "sans-serif"]
      },
      backgroundImage: {
        "hero-grid":
          "radial-gradient(circle at top left, rgba(104, 225, 198, 0.22), transparent 34%), radial-gradient(circle at bottom right, rgba(255, 132, 95, 0.18), transparent 30%), linear-gradient(145deg, #ffffff, #f2f5ff)"
      }
    }
  },
  plugins: []
};
