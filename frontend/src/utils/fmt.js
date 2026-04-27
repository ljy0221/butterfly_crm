export function fmtNum(n) {
  if (typeof n !== "number") return n;
  return n.toLocaleString("en-US");
}
