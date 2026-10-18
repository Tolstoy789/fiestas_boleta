"""
calculo.py - Lógica pura para dividir una boleta entre participantes.

No depende de la interfaz, del OCR ni del modelo: recibe ítems, asignaciones
y porcentajes, y devuelve cuánto paga cada persona. Así sirve igual para una
app en Python (Streamlit/Termux) o como referencia para portarla a Kotlin.

Reglas:
  * Un ítem asignado a varias personas se divide en partes iguales entre ellas.
  * La propina y el impuesto se reparten en proporción al subtotal de cada uno.
  * Si el impuesto ya viene incluido en los precios (caso típico del IVA en
    boletas chilenas), se pasa ``impuesto_incluido=True`` y no se suma nada.
  * Los montos se redondean a la unidad monetaria indicada y el residuo de
    redondeo se asigna a quienes más consumieron, para que la suma cuadre
    exactamente con el total.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from decimal import Decimal, ROUND_DOWN


@dataclass
class Item:
    nombre: str
    precio: Decimal
    cantidad: int = 1

    @property
    def total(self) -> Decimal:
        return self.precio * self.cantidad


@dataclass
class Detalle:
    """Resultado por participante."""

    nombre: str
    items: list[tuple[str, Decimal]] = field(default_factory=list)
    subtotal: Decimal = Decimal("0")
    propina: Decimal = Decimal("0")
    impuesto: Decimal = Decimal("0")
    total: Decimal = Decimal("0")


def dividir_cuenta(
    items: list[Item],
    asignaciones: dict[int, list[str]],
    participantes: list[str],
    propina_pct: Decimal | float = 10,
    impuesto_pct: Decimal | float = 0,
    impuesto_incluido: bool = True,
    unidad: Decimal | int = 1,
) -> dict[str, Detalle]:
    """Calcula lo que debe pagar cada participante.

    Args:
        items: Ítems de la boleta.
        asignaciones: índice del ítem -> lista de participantes que lo consumieron.
            Un ítem sin asignar se reparte entre todos.
        participantes: Nombres de todos los participantes.
        propina_pct: Porcentaje de propina sobre el subtotal (10 = 10 %).
        impuesto_pct: Porcentaje de impuesto, solo se suma si no viene incluido.
        impuesto_incluido: True si los precios ya traen el impuesto.
        unidad: Unidad mínima de redondeo (1 para pesos, Decimal("0.01") para centavos).

    Returns:
        Diccionario nombre -> Detalle.
    """
    if not participantes:
        raise ValueError("Debe haber al menos un participante.")
    desconocidos = {p for ps in asignaciones.values() for p in ps} - set(participantes)
    if desconocidos:
        raise ValueError(f"Participantes no registrados: {sorted(desconocidos)}")

    propina_pct = Decimal(str(propina_pct))
    impuesto_pct = Decimal("0") if impuesto_incluido else Decimal(str(impuesto_pct))
    unidad = Decimal(str(unidad))

    detalles = {p: Detalle(nombre=p) for p in participantes}

    # 1. Subtotales exactos (sin redondear todavía).
    for i, item in enumerate(items):
        quienes = asignaciones.get(i) or participantes
        parte = item.total / len(quienes)
        for p in quienes:
            detalles[p].items.append((item.nombre, parte))
            detalles[p].subtotal += parte

    # 2. Propina e impuesto proporcionales al subtotal.
    for d in detalles.values():
        d.propina = d.subtotal * propina_pct / 100
        d.impuesto = d.subtotal * impuesto_pct / 100
        d.total = d.subtotal + d.propina + d.impuesto

    # 3. Redondeo que cuadra con el gran total.
    _redondear_cuadrando(detalles, unidad)
    return detalles


def _redondear_cuadrando(detalles: dict[str, Detalle], unidad: Decimal) -> None:
    exacto = sum((d.total for d in detalles.values()), Decimal("0"))
    objetivo = (exacto / unidad).to_integral_value() * unidad
    for d in detalles.values():
        d.total = (d.total / unidad).to_integral_value(ROUND_DOWN) * unidad
    residuo = int((objetivo - sum(d.total for d in detalles.values())) / unidad)
    for d in sorted(detalles.values(), key=lambda d: d.subtotal, reverse=True)[:residuo]:
        d.total += unidad


def total_boleta(items: list[Item]) -> Decimal:
    return sum((it.total for it in items), Decimal("0"))
