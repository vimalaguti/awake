import { Telegraph } from "jsr:@dcdunkan/telegraph";
import axios from 'npm:axios@1.8.3';
import * as os from 'node:os';
import * as fs from 'node:fs';
import * as path from 'node:path';

const token = process.env.TELEGRAM_TOKEN;
const myTelegramId = process.env.MY_TELEGRAM_ID;

if (!token || !myTelegramId) {
  console.error("Env variables not defined");
  process.exit(1);
}

const bot = new Telegraph(
    {
        token: token
    }
);

bot.start((ctx) => {
  ctx.reply("I am not a public bot.");
});

bot.help((ctx) => {
  if (ctx.from.id.toString() === myTelegramId) {
    ctx.replyWithMarkdownV2(`
      |/alive check if I am alive
      |/cpu utilization
      |/temperature cpu temperature
    `);
  }
});

bot.command('alive', (ctx) => {
  if (ctx.from.id.toString() === myTelegramId) {
    ctx.reply("Yes");
  }
});

bot.command('temperature', async (ctx) => {
  if (ctx.from.id.toString() === myTelegramId) {
    try {
      const tempPath = '/sys/class/thermal/thermal_zone0/temp';
      const temp = fs.readFileSync(tempPath, 'utf8').trim();
      const tempCelsius = parseInt(temp, 10) / 1000;
      ctx.reply(`${tempCelsius}°C`);
    } catch (error) {
      ctx.reply("Failed to read temperature");
    }
  }
});

bot.launch().then(() => {
  console.log("Telegram bot started.");
  sendStartupMessage(token, myTelegramId);
});

async function sendStartupMessage(token: string, myTelegramId: string) {
  const url = `https://api.telegram.org/bot${token}/sendMessage`;
  const data = {
    chat_id: myTelegramId,
    text: "startup"
  };

  try {
    const response = await axios.post(url, data);
    console.log("startup status code: " + response.status);
  } catch (error) {
    console.error("Failed to send startup message", error);
  }
}