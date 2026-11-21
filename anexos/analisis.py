#!/usr/bin/env python3
"""Análisis y gráficas. Uso: python3 analisis.py resultados/datos.csv
Requiere: pandas, matplotlib, scipy  (sudo pacman -S python-pandas python-matplotlib python-scipy)"""
import os
import sys

import numpy as np
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from scipy import stats

CSV = sys.argv[1] if len(sys.argv) > 1 else "resultados/datos.csv"
OUT = os.path.dirname(os.path.abspath(CSV))
NOMBRES = {"trad": "Monolito tradicional", "mod": "Monolito modular"}
CARGAS = {"normal": "Normal (1 hilo)", "pesada": "Pesada (2 hilos)"}
COLORES = {"trad": "#c0504d", "mod": "#4f81bd"}

df = pd.read_csv(CSV)
df["E_J"] = df.pkg_J + df.dram_J
idle = df[df.arquitectura == "idle"][["bloque", "E_J", "seg"]].copy()
idle["P_idle_W"] = idle.E_J / idle.seg
runs = df[df.arquitectura != "idle"].merge(idle[["bloque", "P_idle_W"]], on="bloque", how="left")
runs["E_neta_J"] = runs.E_J - runs.P_idle_W * runs.seg
runs["uJ_op"] = runs.E_J / runs.ops_totales * 1e6
runs["uJ_op_neto"] = runs.E_neta_J / runs.ops_totales * 1e6
runs["W_medio"] = runs.E_J / runs.seg
runs["kops_s"] = runs.ops_totales / runs.seg / 1e3

def welch_ci(x, y, conf=0.95):
    vx, vy = x.var(ddof=1) / len(x), y.var(ddof=1) / len(y)
    se = np.sqrt(vx + vy)
    gl = (vx + vy) ** 2 / (vx ** 2 / (len(x) - 1) + vy ** 2 / (len(y) - 1))
    t = stats.t.ppf((1 + conf) / 2, gl)
    d = y.mean() - x.mean()
    return d - t * se, d + t * se

METRICAS = [("uJ_op", "Energía total (µJ/op)"), ("uJ_op_neto", "Energía neta sin reposo (µJ/op)"),
            ("seg", "Tiempo (s)"), ("W_medio", "Potencia media (W)")]
filas = []
for carga in ("normal", "pesada"):
    for col, nombre in METRICAS:
        a = runs[(runs.arquitectura == "trad") & (runs.carga == carga)].sort_values("bloque")
        b = runs[(runs.arquitectura == "mod") & (runs.carga == carga)].sort_values("bloque")
        x, y = a[col].values, b[col].values
        if len(x) < 2 or len(y) < 2:
            continue
        sp = np.sqrt(((len(x) - 1) * x.var(ddof=1) + (len(y) - 1) * y.var(ddof=1)) / (len(x) + len(y) - 2))
        lo, hi = welch_ci(x, y)
        par = a[["bloque", col]].merge(b[["bloque", col]], on="bloque", suffixes=("_t", "_m"))
        filas.append({
            "carga": carga, "metrica": col, "n_trad": len(x), "n_mod": len(y),
            "media_trad": x.mean(), "sd_trad": x.std(ddof=1), "media_mod": y.mean(), "sd_mod": y.std(ddof=1),
            "dif_pct": (y.mean() - x.mean()) / x.mean() * 100,
            "ic_lo_pct": lo / x.mean() * 100, "ic_hi_pct": hi / x.mean() * 100,
            "shapiro_p_trad": stats.shapiro(x).pvalue if len(x) >= 3 else np.nan,
            "shapiro_p_mod": stats.shapiro(y).pvalue if len(y) >= 3 else np.nan,
            "p_welch": stats.ttest_ind(x, y, equal_var=False).pvalue,
            "p_mannwhitney": stats.mannwhitneyu(x, y, alternative="two-sided").pvalue,
            "p_pareada_bloque": stats.ttest_rel(par[col + "_t"], par[col + "_m"]).pvalue if len(par) >= 3 else np.nan,
            "cohen_d": (y.mean() - x.mean()) / sp if sp > 0 else np.nan,
        })
tabla = pd.DataFrame(filas)
tabla.to_csv(os.path.join(OUT, "resumen_estadistico.csv"), index=False)
pd.set_option("display.width", 220)
pd.set_option("display.max_columns", 30)
print(tabla.round(4).to_string(index=False))
print(f"\nTemperatura máx. registrada: {runs.temp_C.max()} °C | potencia en reposo media: {idle.P_idle_W.mean():.2f} W")

def cajas(ax, col, ylabel, titulo):
    datos, pos, cols = [], [], []
    for j, carga in enumerate(("normal", "pesada")):
        for i, arq in enumerate(("trad", "mod")):
            datos.append(runs[(runs.arquitectura == arq) & (runs.carga == carga)][col].values)
            pos.append(j + (i - 0.5) * 0.35)
            cols.append(COLORES[arq])
    bp = ax.boxplot(datos, positions=pos, widths=0.28, patch_artist=True, showmeans=True)
    for patch, c in zip(bp["boxes"], cols):
        patch.set_facecolor(c)
        patch.set_alpha(0.7)
    ax.set_xticks([0, 1])
    ax.set_xticklabels([CARGAS["normal"], CARGAS["pesada"]])
    ax.set_ylabel(ylabel)
    ax.set_title(titulo)
    ax.grid(axis="y", alpha=0.3)
    ax.legend([plt.Rectangle((0, 0), 1, 1, color=COLORES[k], alpha=0.7) for k in ("trad", "mod")],
              [NOMBRES["trad"], NOMBRES["mod"]], fontsize=8)

def deriva(ax, col, ylabel, titulo):
    for arq in ("trad", "mod"):
        for carga, ls in (("normal", "-"), ("pesada", "--")):
            s = runs[(runs.arquitectura == arq) & (runs.carga == carga)].sort_values("bloque")
            ax.plot(s.bloque, s[col], ls, marker="o", color=COLORES[arq], label=f"{NOMBRES[arq]} · {CARGAS[carga]}")
    ax.set_xlabel("Bloque (orden de ejecución)")
    ax.set_ylabel(ylabel)
    ax.set_title(titulo)
    ax.grid(alpha=0.3)
    ax.legend(fontsize=7)

PANELES = [
    ("fig1_energia_por_operacion", lambda ax: cajas(ax, "uJ_op", "µJ por operación", "Energía por operación (total)")),
    ("fig2_tiempo", lambda ax: cajas(ax, "seg", "Segundos", "Tiempo para completar el mismo trabajo")),
    ("fig3_potencia", lambda ax: cajas(ax, "W_medio", "Vatios", "Potencia media (paquete + DRAM)")),
    ("fig4_estabilidad", lambda ax: deriva(ax, "uJ_op", "µJ por operación", "Estabilidad entre bloques")),
]
for nombre, fn in PANELES:
    fig, ax = plt.subplots(figsize=(6.5, 4.2))
    fn(ax)
    fig.tight_layout()
    fig.savefig(os.path.join(OUT, nombre + ".png"), dpi=200)
    plt.close(fig)

fig, axs = plt.subplots(2, 2, figsize=(13, 8.5))
for ax, (_, fn) in zip(axs.ravel(), PANELES):
    fn(ax)
fig.tight_layout()
fig.savefig(os.path.join(OUT, "fig_resumen.png"), dpi=200)
plt.close(fig)

if not tabla.empty:
    fig, ax = plt.subplots(figsize=(7.5, 4.6))
    for k, (col, lab) in enumerate((("uJ_op", "Energía total por operación"),
                                    ("uJ_op_neto", "Energía neta (sin reposo) por operación"))):
        sub = tabla[tabla.metrica == col].reset_index(drop=True)
        xs = np.arange(len(sub)) + (k - 0.5) * 0.35
        ax.bar(xs, sub.dif_pct, 0.33, label=lab, alpha=0.85,
               yerr=[sub.dif_pct - sub.ic_lo_pct, sub.ic_hi_pct - sub.dif_pct], capsize=5)
    ax.axhline(0, color="k", lw=1)
    ax.set_xticks(range(len(sub)))
    ax.set_xticklabels([CARGAS[c] for c in sub.carga])
    ax.set_ylabel("Diferencia modular vs tradicional (%)")
    ax.set_title("Diferencia relativa de consumo (IC 95 %)\n>0: el modular consume más · <0: consume menos")
    ax.grid(axis="y", alpha=0.3)
    ax.legend(fontsize=8)
    fig.tight_layout()
    fig.savefig(os.path.join(OUT, "fig5_diferencia_relativa.png"), dpi=200)
    plt.close(fig)

print(f"\nGráficas y resumen_estadistico.csv guardados en {OUT}")
