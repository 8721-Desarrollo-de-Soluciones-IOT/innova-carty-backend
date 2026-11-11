Feature: Recepción e ingesta de telemetría desde el dispositivo edge (TS01)
  Como plataforma de Innova Carty
  Quiero recibir el estado y los eventos que envía el ESP32 de cada carrito
  Para mantener actualizado el estado del carrito para la consola, la app y el propio carrito

  Scenario: El carrito reporta su estado con una clave de dispositivo válida
    Given un carrito inteligente nuevo conectado a la tienda
    When el carrito reporta los siguientes tags RFID:
      | tag            | cantidad |
      | 04414793C92A81 | 1        |
    Then la respuesta tiene el código 200
    And el carrito queda en estado "IN_SESSION"

  Scenario: Se rechaza la telemetría con una clave de dispositivo inválida
    Given un carrito inteligente nuevo conectado a la tienda
    When el carrito reporta su estado con la clave de dispositivo "clave-incorrecta"
    Then la respuesta tiene el código 401

  Scenario: El carrito cruza la salida sin pagar
    Given un carrito inteligente nuevo conectado a la tienda
    And el carrito reporta los siguientes tags RFID:
      | tag            | cantidad |
      | 04414793C92A81 | 1        |
    When el carrito reporta el evento "GEOFENCE" en la zona "Salida 1"
    Then el carrito queda en estado "LOCKED"
    And la consola muestra una alerta "GEOFENCE" abierta para el carrito
