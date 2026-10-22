// Testa o aviso de versão nova: entra como admin e mostra o que chegou no chat.
// Uso: node teste_atualizacao.js
const mineflayer = require('mineflayer');

const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Organizador', version: '1.21.4', auth: 'offline' });
const chat = [];
bot.on('resourcePack', () => bot.acceptResourcePack());
bot.on('messagestr', (m) => chat.push(m));
bot.once('spawn', () => setTimeout(() => {
  console.log('Chat ao entrar:\n  ' + chat.join('\n  '));
  bot.quit();
  setTimeout(() => process.exit(0), 500);
}, 6000));
bot.on('error', (e) => { console.error(e); process.exit(1); });
