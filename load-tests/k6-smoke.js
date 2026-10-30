import http from "k6/http";
import ws from "k6/ws";
import { check, sleep } from "k6";

export const options = {
  vus: Number(__ENV.VUS || 1),
  duration: __ENV.DURATION || "30s",
};

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const wsUrl = (__ENV.WS_URL || "ws://localhost:8080/ws");
const token = __ENV.JWT || "";
const tripId = __ENV.TRIP_ID || "trip-1";

export default function () {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};

  const publish = http.post(
    `${baseUrl}/api/v1/trips/${tripId}/events`,
    JSON.stringify({
      eventId: `k6-${__VU}-${Date.now()}`,
      eventType: "LOCATION_UPDATE",
      occurredAt: new Date().toISOString(),
      payload: {
        latitude: 28.6139,
        longitude: 77.209,
        accuracyMeters: 8,
      },
    }),
    { headers: { ...headers, "Content-Type": "application/json" } }
  );
  check(publish, {
    "publish accepted": (response) => [200, 201].includes(response.status),
  });

  ws.connect(wsUrl, { headers }, (socket) => {
    socket.on("open", () => {
      socket.send(JSON.stringify({
        action: "SUBSCRIBE",
        requestId: `req-${__VU}`,
        topic: `trip:${tripId}`,
      }));
    });
    socket.setTimeout(() => socket.close(), 1000);
  });

  sleep(1);
}
