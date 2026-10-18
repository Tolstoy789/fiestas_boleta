from decimal import Decimal

import pytest

from calculo import Item, dividir_cuenta


def items_ejemplo():
    return [
        Item("Pizza", Decimal("12000")),
        Item("Cerveza", Decimal("3500"), cantidad=2),
        Item("Bebida", Decimal("2000")),
    ]


def test_item_compartido_y_propina():
    r = dividir_cuenta(
        items_ejemplo(),
        {0: ["Ana", "Beto"], 1: ["Beto"], 2: ["Ana"]},
        ["Ana", "Beto"],
        propina_pct=10,
    )
    assert r["Ana"].subtotal == Decimal("8000")
    assert r["Beto"].subtotal == Decimal("13000")
    assert r["Ana"].total == Decimal("8800")
    assert r["Beto"].total == Decimal("14300")


def test_item_sin_asignar_se_reparte_entre_todos():
    r = dividir_cuenta([Item("Pan", Decimal("3000"))], {}, ["A", "B", "C"], propina_pct=0)
    assert [r[p].total for p in "ABC"] == [Decimal("1000")] * 3


def test_redondeo_cuadra_con_total():
    r = dividir_cuenta([Item("Torta", Decimal("10000"))], {}, ["A", "B", "C"], propina_pct=10)
    assert sum(d.total for d in r.values()) == Decimal("11000")


def test_impuesto_aparte():
    r = dividir_cuenta(
        [Item("Plato", Decimal("10.00"))], {}, ["A"],
        propina_pct=15, impuesto_pct=8, impuesto_incluido=False, unidad=Decimal("0.01"),
    )
    assert r["A"].total == Decimal("12.30")


def test_participante_desconocido():
    with pytest.raises(ValueError):
        dividir_cuenta(items_ejemplo(), {0: ["Zoe"]}, ["Ana"])
