import { useTheme } from "../context/ThemeContext";

const labels = {
  white: "White",
  black: "Black"
};

export function ThemeSwitcher() {
  const { theme, setTheme, themes } = useTheme();

  return (
    <div className="theme-switcher" aria-label="Choose page colour theme">
      {themes.map((item) => (
        <button
          key={item}
          type="button"
          className={`theme-pill ${theme === item ? "theme-pill-active" : ""}`}
          onClick={() => setTheme(item)}
        >
          <span className={`theme-dot theme-dot-${item}`} />
          {labels[item]}
        </button>
      ))}
    </div>
  );
}
