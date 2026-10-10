import { createServer } from "node:http";
import { appendFileSync, mkdirSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";

const port = Number(process.env.PORT ?? 9000);
const delayMs = Number(process.env.DELAY_MS ?? 50);
const failRate = Number(process.env.FAIL_RATE ?? 0);
const output = process.env.OUT ?? "mock-push-received.csv";

mkdirSync(dirname(output), { recursive: true });
writeFileSync(output, "received_at,message_count,delay_ms,failed_count\n");

const stats = { requests: 0, messages: 0, firstReceivedAt: null, lastReceivedAt: null };

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const readJson = (request) =>
  new Promise((resolve, reject) => {
    let body = "";
    request.on("data", (chunk) => (body += chunk));
    request.on("end", () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch (error) {
        reject(error);
      }
    });
    request.on("error", reject);
  });

const respondJson = (response, status, payload) => {
  response.writeHead(status, { "content-type": "application/json" });
  response.end(JSON.stringify(payload));
};

createServer(async (request, response) => {
  if (request.method === "POST" && request.url === "/send") {
    const receivedAt = new Date();
    const { messages = [] } = await readJson(request);
    const failed = messages
      .filter(() => Math.random() < failRate)
      .map((message) => message.notificationId);
    stats.requests += 1;
    stats.messages += messages.length;
    stats.firstReceivedAt ??= receivedAt;
    stats.lastReceivedAt = receivedAt;
    appendFileSync(output, `${receivedAt.toISOString()},${messages.length},${delayMs},${failed.length}\n`);
    await sleep(delayMs);
    return respondJson(response, 200, { failedNotificationIds: failed });
  }
  if (request.method === "GET" && request.url === "/stats") {
    const spreadMs =
      stats.firstReceivedAt && stats.lastReceivedAt ? stats.lastReceivedAt - stats.firstReceivedAt : null;
    return respondJson(response, 200, { ...stats, spreadMs, delayMs, failRate });
  }
  if (request.method === "POST" && request.url === "/reset") {
    Object.assign(stats, { requests: 0, messages: 0, firstReceivedAt: null, lastReceivedAt: null });
    writeFileSync(output, "received_at,message_count,delay_ms,failed_count\n");
    return respondJson(response, 200, { reset: true });
  }
  respondJson(response, 404, { error: "not found" });
}).listen(port, () => {
  console.log(`mock push server listening on :${port} delay=${delayMs}ms failRate=${failRate} out=${output}`);
});
