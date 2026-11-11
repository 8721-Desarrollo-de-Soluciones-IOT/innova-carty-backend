Feature: Creación y consulta de órdenes de compra (TS02)
  Como plataforma de Innova Carty
  Quiero registrar cada compra como una orden con sus productos y su total
  Para que el comprador y el administrador puedan consultarla

  Background:
    Given un carrito inteligente nuevo conectado a la tienda
    And un comprador registrado en la app

  Scenario: La orden se crea al leer el primer producto
    When el carrito reporta los siguientes tags RFID:
      | tag            | cantidad |
      | 04414793C92A81 | 1        |
      | 044D4593C92A81 | 2        |
    And el administrador consulta las órdenes de compra del carrito
    Then la orden aparece con estado "ACTIVE", 2 líneas de producto y un total de S/ 9.80

  Scenario: El comprador consulta su orden pagada
    Given el carrito reporta los siguientes tags RFID:
      | tag            | cantidad |
      | 04414793C92A81 | 1        |
    And el comprador vincula el carrito escaneando su QR
    And el comprador paga con "PLIN"
    When el comprador consulta sus compras
    Then la orden aparece con estado "PAID", 1 líneas de producto y un total de S/ 4.80
