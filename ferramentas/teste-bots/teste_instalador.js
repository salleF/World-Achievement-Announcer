// Testa a instalação guiada da voz pelo comando /sinos instalar-voz confirmar e mostra o progresso.
// Uso: node teste_instalador.js
const mineflayer = require('mineflayer');

const espera = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Organizador', version: '1.21.4', auth: 'offline' });
  const inicio = Date.now();
  let fim = false;
  bot.on('resourcePack', () => bot.acceptResourcePack());
  bot.on('messagestr', (m) => {
    if (m.startsWith('[Voz]') || m.startsWith('Instalação') || m.startsWith('Para continuar')) {
      console.log(`${((Date.now() - inicio) / 1000).toFixed(0).padStart(4)}s ${m}`);
      if (/Pronto!|parou|ainda falta|não encontrado/.test(m)) fim = true;
    }
  });
  await new Promise((r) => bot.once('spawn', r));
  await espera(3000);
  bot.chat('/sinos instalar-voz');
  await espera(1500);
  bot.chat('/sinos instalar-voz confirmar');
  for (let i = 0; i < 900 && !fim; i++) await espera(1000);
  await espera(2000);
  bot.chat('/stop');
  await espera(2000);
  process.exit(0);
}

main().catch((e) => { console.error(e); process.exit(1); });
