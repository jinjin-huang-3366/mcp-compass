import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "MCP Compass",
  description: "Find the right MCP for your agent requirement",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
