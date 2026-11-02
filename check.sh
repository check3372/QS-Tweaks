#!/usr/bin/env bash
# QSTweaks 静态自检（不需要 Android SDK）
# 用法：bash check.sh [工程根目录]
set -u
ROOT="${1:-$(cd "$(dirname "$0")" && pwd)}"
JAVA="$ROOT/app/src/main/java/com/firefly/qstweaks"
RES="$ROOT/app/src/main/res"
MAN="$ROOT/app/src/main/AndroidManifest.xml"
FAIL=0

echo "== Java 文件 =="
ls "$JAVA" | sed 's/^/  /'

echo
echo "== 重复类定义 =="
grep -rhoE '^\s*(public |final |abstract )*(class|enum|interface) [A-Za-z0-9_]+' "$JAVA" \
  | awk '{print $NF}' | sort | uniq -c | awk '$1>1{print "  DUPLICATE:",$2; d=1} END{if(!d)print "  OK"}'

echo
echo "== 花括号平衡 =="
for f in "$JAVA"/*.java; do
  o=$(tr -cd '{' < "$f" | wc -c); c=$(tr -cd '}' < "$f" | wc -c)
  [ "$o" -ne "$c" ] && { echo "  UNBALANCED: $(basename "$f")  {=$o }=$c"; FAIL=1; }
done
echo "  done"

echo
echo "== R.* 资源引用是否都有定义 =="
grep -rhoE 'R\.(drawable|id|color|string|mipmap)\. [a-z0-9_]+' "$JAVA" >/dev/null 2>&1
grep -rhoE 'R\.(drawable|id|color|string|mipmap)[.][a-z0-9_]+' "$JAVA" | sort -u | while read -r r; do
  t=${r%%.*}; t=${t#R.}
  n=${r##*.}
  ok=0
  case "$t" in
    drawable) [ -f "$RES/drawable/$n.xml" ] && ok=1 ;;
    id|string|color) grep -rq "\"$n\"\|name=\"$n\"" "$RES/values" 2>/dev/null && ok=1 ;;
    mipmap) ls "$RES"/mipmap-*/"$n".xml >/dev/null 2>&1 && ok=1 ;;
  esac
  [ $ok -eq 0 ] && echo "  MISSING: $r"
done
echo "  done"

echo
echo "== Manifest 组件对应的 Java 文件 =="
grep -oE 'android:name="\.[A-Za-z0-9_]+"' "$MAN" | sed -E 's/.*"\.([A-Za-z0-9_]+)"/\1/' | sort -u | while read -r cls; do
  if [ -f "$JAVA/$cls.java" ]; then echo "  ok: $cls"; else echo "  MISSING: $cls"; fi
done

echo
echo "== 已删除类是否仍被引用 =="
for sym in XposedActive TileFactory ReflectiveTile; do
  refs=$(grep -rl "\b$sym\b" "$JAVA" | grep -v "/$sym.java" | wc -l)
  [ "$refs" -gt 0 ] && { echo "  BROKEN: $sym 仍被引用"; FAIL=1; }
done
echo "  done"

exit $FAIL