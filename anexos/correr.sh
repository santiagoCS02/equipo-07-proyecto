#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")"

REPS="${REPS:-8}"
IDLE_SECS="${IDLE_SECS:-15}"
COOLDOWN="${COOLDOWN:-5}"
OUT="${OUT:-resultados}"
JVM="-Xms256m -Xmx256m -XX:+UseSerialGC"
CSV="$OUT/datos.csv"
mkdir -p "$OUT" build

echo "== 1/5 Compilando =="
javac -d build Bench.java

echo "== 2/5 Permisos RAPL (se pierden al reiniciar) =="
sudo chmod a+r /sys/class/powercap/intel-rapl:0*/energy_uj
cat /sys/class/powercap/intel-rapl:0/energy_uj > /dev/null

echo "== 3/5 Verificando equivalencia funcional =="
java $JVM -cp build Bench verify

echo "== 4/5 Ajustes del sistema y calibración =="
echo performance | sudo tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor > /dev/null || true
echo 1 | sudo tee /sys/devices/system/cpu/intel_pstate/no_turbo > /dev/null 2>&1 || true
OPS="${OPS:-$(java $JVM -cp build Bench cal)}"
echo "Operaciones por hilo en la ventana medida: $OPS"

if [[ -f "$CSV" ]]; then mv "$CSV" "$CSV.$(date +%s).bak"; fi
echo "bloque,arquitectura,carga,hilos,ops_totales,seg,pkg_J,dram_J,temp_C,checksum" > "$CSV"

echo "== 5/5 Midiendo: $REPS bloques (cada uno: reposo + 4 condiciones en orden aleatorio) =="
CONDS=("trad normal" "trad pesada" "mod normal" "mod pesada")
for b in $(seq 1 "$REPS"); do
  echo "--- bloque $b/$REPS  [$(date +%T)] ---"
  java $JVM -cp build Bench idle "$b" "$IDLE_SECS" >> "$CSV"
  mapfile -t ORDER < <(printf '%s\n' "${CONDS[@]}" | shuf)
  for c in "${ORDER[@]}"; do
    read -r arch load <<< "$c"
    echo "  [$(date +%T)] $arch $load"
    java $JVM -cp build Bench run "$arch" "$load" "$b" "$OPS" >> "$CSV"
    sleep "$COOLDOWN"
  done
done
echo "Listo. Datos en $CSV  ->  python3 analisis.py $CSV"
