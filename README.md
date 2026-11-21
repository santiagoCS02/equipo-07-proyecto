# Proyecto LaTeX — Metodología de la Investigación (UPTC)

Relación entre arquitectura de software (monolito tradicional vs. monolito modular) y consumo energético. Formato NTC 1486 (ICONTEC) con referencias IEEE.

## Estructura

```
equipo-07-proyecto/
├── .gitignore
├── README.md
├── main.tex                     documento principal
├── main.pdf                     PDF compilado
├── referencias.bib              35 referencias (estilo IEEE)
├── texstyles/
│   └── IEEEtran.bst
├── capitulos/
│   ├── planteamiento_problema.tex   Capítulo 1
│   ├── justificacion.tex            Capítulo 2
│   ├── objetivos.tex                Capítulo 3
│   ├── alcance_investigacion.tex    Capítulo 4
│   ├── formulacion_hipotesis.tex    Capítulo 5
│   ├── variables.tex                Capítulo 6
│   ├── marco_referencial.tex        Capítulo 7
│   ├── diseno_metodologico.tex      Capítulo 8
│   ├── resultados_discusion.tex     Capítulo 9
│   ├── conclusiones_recomendaciones.tex   Capítulo 10
│   └── anexos.tex
├── figuras/
│   ├── fig_energia_por_operacion.png
│   ├── fig_tiempo.png
│   ├── fig_potencia_media.png
│   ├── fig_estabilidad_bloques.png
│   └── fig_diferencia_relativa.png
└── anexos/
    ├── Bench.java               banco de pruebas
    ├── correr.sh                ejecución por bloques
    ├── ejecutar.txt             órdenes de la corrida
    ├── analisis.py              análisis estadístico y gráficas
    ├── corrida.log              registro de la corrida
    └── resultados/
        ├── datos.csv
        └── resumen_estadistico.csv
```

## Compilación

```
pdflatex main.tex
bibtex main
pdflatex main.tex
pdflatex main.tex
```

Se recomienda una instalación completa de TeX Live (u Overleaf) para contar con el paquete de idioma español.

## Reproducir el experimento

Desde `anexos/`:

```
REPS=12 ./correr.sh
python3 analisis.py resultados/datos.csv
```

El análisis requiere Python con `numpy`, `pandas`, `scipy` y `matplotlib`.

## Pendientes

- Cifras completas del flujo PRISMA (sección 1.1.7).
- Versión de Java y del kernel usados en la corrida (sección 8.4).
- Instrumentos de fases anteriores (matriz de correlación, ficha de causas, diagrama de Ishikawa) en los anexos.
