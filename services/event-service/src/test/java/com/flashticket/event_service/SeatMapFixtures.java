package com.flashticket.event_service;

final class SeatMapFixtures {
  static final String LAYOUT = "10000000-0000-4000-8000-000000000001";
  static final String SECTOR = "10000000-0000-4000-8000-000000000002";
  static final String SEAT = "10000000-0000-4000-8000-000000000003";
  static final String HIDDEN = "10000000-0000-4000-8000-000000000004";
  static final String TYPE = "10000000-0000-4000-8000-000000000005";

  static String map(boolean assigned) {
    return """
        {"schemaVersion":1,"layoutId":"%s","name":"Main hall","canvasWidth":800,"canvasHeight":600,
         "backgroundImageReference":null,"layoutData":{"schemaVersion":1,"decorations":[]},
         "sectors":[{"sectorId":"%s","name":"A","code":"A","kind":"SEATED",
          "configuredCapacity":"2","displayOrder":0,"colorCode":"#123456",
          "geometry":{"schemaVersion":1,"shape":{"shapeType":"rectangle","bounds":{"x":0,"y":0,"width":100,"height":100}},
           "seatLayout":{"mode":"grid","rows":1,"seatsPerRow":2,"gapX":10,"gapY":10,"paddingX":0,"paddingY":0,"offsetX":0,"offsetY":0,"seatRadius":4,"rowStartCharCode":65,"seatStartNumber":1},
           "editorVisible":true,"editorLocked":false},
          "seats":[{"seatId":"%s","seatLabel":"A1","rowName":"A","seatNumber":"1","coordX":1.25,"coordY":2,"ticketTypeId":%s,"isHidden":false},
                   {"seatId":"%s","seatLabel":"A2","rowName":"A","seatNumber":"2","coordX":3,"coordY":2,"ticketTypeId":null,"isHidden":true}]}]}
        """
        .formatted(LAYOUT, SECTOR, SEAT, assigned ? "\"" + TYPE + "\"" : "null", HIDDEN);
  }

  static String types(String version) {
    return """
        {"rowVersion":"%s","ticketTypes":[{"ticketTypeId":null,"sectorId":"%s","name":"Standard","unitPrice":"100000","currency":"VND","colorCode":"#123456"}]}
        """
        .formatted(version, SECTOR);
  }
}
