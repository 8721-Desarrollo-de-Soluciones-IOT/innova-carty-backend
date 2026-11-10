Feature: Visualización dinámica del total acumulado (US05)
  Como comprador moderno
  Quiero ver en mi celular los productos y el total de mi carrito en tiempo real
  Para controlar cuánto voy gastando

  Background:
    Given un carrito inteligente nuevo conectado a la tienda
    And un comprador registrado en la app

  Scenario: El comprador vincula el carrito y ve su total
    Given el carrito reporta los siguientes tags RFID:
      | tag            | cantidad |
      | 04414793C92A81 | 1        |
    When el comprador vincula el carrito escaneando su QR
    And el comprador consulta su carrito
    Then la app muestra 1 productos y un total de S/ 4.80

  Scenario: El total se actualiza al agregar productos
    Given el comprador vincula el carrito escaneando su QR
    When el carrito reporta los siguientes tags RFID:
      | tag            | cantidad |
      | 04414793C92A81 | 1        |
      | 044D4593C92A81 | 2        |
    And el comprador consulta su carrito
    Then la app muestra 3 productos y un total de S/ 9.80
