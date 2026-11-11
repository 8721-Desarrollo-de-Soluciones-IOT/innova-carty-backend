Feature: Registro automático de artículo mediante lectura RFID (US06)
  Como comprador moderno
  Quiero que los productos que coloco en el carrito se registren solos
  Para no tener que escanear cada artículo

  Scenario: Los tags leídos se registran como productos del catálogo
    Given un carrito inteligente nuevo conectado a la tienda
    When el carrito reporta los siguientes tags RFID:
      | tag            | cantidad |
      | 04414793C92A81 | 1        |
      | 044D4593C92A81 | 2        |
    Then el carrito tiene 3 productos y un total de S/ 9.80

  Scenario: Un tag que no está en el catálogo no se cobra
    Given un carrito inteligente nuevo conectado a la tienda
    When el carrito reporta los siguientes tags RFID:
      | tag            | cantidad |
      | 04414793C92A81 | 1        |
      | 04FFFFFFFFFFFF | 1        |
    Then el carrito tiene 1 productos y un total de S/ 4.80

  Scenario: El peso no coincide con los productos leídos
    Given un carrito inteligente nuevo conectado a la tienda
    And el carrito reporta los siguientes tags RFID:
      | tag            | cantidad |
      | 04414793C92A81 | 1        |
      | 044D4593C92A81 | 2        |
    When la celda de carga reporta un peso estable de 1700 g
    Then el carrito queda en estado "DISCREPANCY"
    And la consola muestra una alerta "WEIGHT" abierta para el carrito
