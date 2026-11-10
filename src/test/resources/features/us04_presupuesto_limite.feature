Feature: Configuración de presupuesto límite de compra (US04)
  Como comprador moderno
  Quiero fijar un presupuesto para mi compra
  Para no gastar más de lo planeado

  Background:
    Given un carrito inteligente nuevo conectado a la tienda
    And un comprador registrado en la app
    And el comprador vincula el carrito escaneando su QR

  Scenario: El comprador fija su presupuesto
    When el comprador fija un presupuesto de S/ 50.00
    Then la respuesta tiene el código 200
    And el presupuesto del carrito es S/ 50.00

  Scenario: No se acepta un presupuesto negativo
    When el comprador fija un presupuesto de S/ -10.00
    Then la respuesta tiene el código 409
