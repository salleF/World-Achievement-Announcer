// Testa a parte 2 do painel: Música (música própria, ritmo, estender, prévia), Sino, Contribuidores e Pacote.
// Precisa de vitoria.mp3 e fanfarra-composta.wav em run/plugins/SinosDoMundo/musicas. Uso: node teste_menu2.js
const mineflayer = require('mineflayer');

const espera = (ms) => new Promise((r) => setTimeout(r, ms));

function entrar(nome) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: nome, version: '1.21.4', auth: 'offline' });
    bot.msgs = [];
    bot.packs = [];
    bot.on('resourcePack', (url) => { bot.packs.push(String(url)); bot.acceptResourcePack(); });
    bot._client.on('add_resource_pack', (d) => bot.packs.push(d.url));
    bot.on('messagestr', (m) => bot.msgs.push(m));
    bot.once('spawn', () => resolve(bot));
    bot.once('error', reject);
  });
}

function janela(bot, ms = 4000) {
  return new Promise((resolve) => {
    const t = setTimeout(() => resolve(null), ms);
    bot.once('windowOpen', (w) => { clearTimeout(t); setTimeout(() => resolve(w), 300); });
  });
}

function titulo(w) {
  const s = JSON.stringify(w && w.title);
  return ([...String(s).matchAll(/text\\?":\\?"([^"\\]+)/g)].map((m) => m[1]).join('') || s).slice(0, 50);
}

async function abrir(bot, ...slots) {
  let p = janela(bot);
  bot.chat('/sinos');
  let w = await p;
  for (const s of slots) {
    p = janela(bot);
    await bot.clickWindow(s.slot ?? s, s.botao ?? 0, s.modo ?? 0);
    w = await p;
  }
  return w;
}

async function main() {
  const org = await entrar('Organizador');
  await espera(4000);

  // 1) Música > Colocar música própria: lista
  let w = await abrir(org, 13, 15);
  const itens = [];
  for (let i = 9; i < 45; i++) if (w && w.slots[i] && w.slots[i].name !== 'gray_stained_glass_pane') itens.push(`${i}:${w.slots[i].name}`);
  console.log('Tela:', titulo(w), '| itens:', itens.join(' '));

  // 2) Escolher vitoria.mp3 (fanfarra-composta.wav vem antes na ordem alfabética: slot 10; vitoria: slot 11)
  let p = janela(org, 60000);
  await org.clickWindow(11, 0, 0);
  w = await p; // confirmação do ritmo
  console.log('Pergunta 1:', titulo(w));
  p = janela(org, 60000);
  await org.clickWindow(11, 0, 0); // confirmar ritmo
  w = await p; // confirmação de estender
  console.log('Pergunta 2:', titulo(w));
  p = janela(org, 90000);
  await org.clickWindow(11, 0, 0); // confirmar estender
  w = await p; // reenviar pacote?
  console.log('Pergunta 3:', titulo(w));
  p = janela(org);
  await org.clickWindow(11, 0, 0); // reenviar
  await p;
  console.log('Mensagens:', org.msgs.filter((m) => /convertida|ajustadas|estendida|reenviado|Não foi/.test(m)).join(' | '));

  // 3) Prévia (Shift + clique) da música composta
  if (org.currentWindow) org.closeWindow(org.currentWindow);
  await espera(500);
  w = await abrir(org, 13, 15);
  const antes = org.packs.length;
  await org.clickWindow(10, 0, 1);
  await espera(8000);
  console.log('Prévia: pacotes recebidos depois do clique:', org.packs.slice(antes).map((u) => u.replace(/^.*\//, '')).join(', ') || '(nenhum)');

  // 4) Sino: tamanho +0,1
  if (org.currentWindow) org.closeWindow(org.currentWindow);
  await espera(500);
  w = await abrir(org, 11);
  console.log('Tela:', titulo(w));
  await org.clickWindow(10, 0, 0);
  await espera(800);

  // 5) Contribuidores: trocar o modo
  if (org.currentWindow) org.closeWindow(org.currentWindow);
  await espera(500);
  w = await abrir(org, 14);
  console.log('Tela:', titulo(w));
  await org.clickWindow(10, 0, 0);
  await espera(800);

  // 6) Pacote: ver o endereço
  if (org.currentWindow) org.closeWindow(org.currentWindow);
  await espera(500);
  w = await abrir(org, 15);
  console.log('Tela:', titulo(w));
  await org.clickWindow(4, 0, 0);
  await espera(800);
  console.log('Endereço:', org.msgs.filter((m) => m.includes('Endereço do pacote')).pop());

  if (org.currentWindow) org.closeWindow(org.currentWindow);
  org.quit();
  await espera(1000);
  process.exit(0);
}

main().catch((e) => { console.error(e); process.exit(1); });
