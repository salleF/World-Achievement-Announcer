// Testa o menu /sinos: navegação, editar campos, criar conquista, amostra de narrador e teste só para o admin.
// Uso: node teste_menu.js
const mineflayer = require('mineflayer');

const espera = (ms) => new Promise((r) => setTimeout(r, ms));

function entrar(nome) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: nome, version: '1.21.4', auth: 'offline' });
    bot.msgs = [];
    bot.displays = 0;
    bot.on('resourcePack', () => bot.acceptResourcePack());
    bot.on('messagestr', (m) => bot.msgs.push(m));
    bot.on('entitySpawn', (e) => { if (e.name === 'item_display') bot.displays++; });
    bot.once('spawn', () => resolve(bot));
    bot.once('error', reject);
  });
}

function nome(item) {
  if (!item) return null;
  let n = item.customName;
  if (n && typeof n === 'object') n = JSON.stringify(n);
  n = String(n || item.name);
  // tira formatação JSON e deixa só o texto
  const textos = [...n.matchAll(/"text":"([^"]*)"/g)].map((m) => m[1]).join('');
  return (textos || n).slice(0, 60);
}

function proximaJanela(bot) {
  return new Promise((resolve) => bot.once('windowOpen', (w) => setTimeout(() => resolve(w), 300)));
}

function listar(titulo, janela, ate) {
  const itens = [];
  for (let i = 0; i < ate; i++) {
    const it = janela.slots[i];
    if (it && it.name !== 'gray_stained_glass_pane') itens.push(`${i}:${nome(it)}`);
  }
  console.log(`\n[${titulo}] ${itens.join(' | ')}`);
}

async function clicar(bot, slot, botao = 0, modo = 0) {
  const p = proximaJanela(bot);
  await bot.clickWindow(slot, botao, modo);
  return Promise.race([p, espera(2500).then(() => null)]);
}

async function main() {
  const org = await entrar('Organizador');
  await espera(4500);
  const b = await entrar('Bot_B');
  await espera(3000);

  // 1) Menu principal
  let p = proximaJanela(org);
  org.chat('/sinos');
  let w = await p;
  listar('Menu principal', w, 27);

  // 2) Conquistas
  w = await clicar(org, 10);
  listar('Conquistas', w, 54);

  // 3) Editor da primeira conquista
  w = await clicar(org, 0);
  listar('Editor', w, 45);
  const antes = nome(w.slots[21]);
  await org.clickWindow(21, 0, 0); // liga/desliga "só uma vez"
  await espera(800);
  console.log(`Toggle "uma vez": antes "${antes}" -> depois "${nome(org.currentWindow.slots[21])}"`);
  await org.clickWindow(21, 0, 0); // volta como estava
  await espera(500);

  // 4) Criar conquista pelo chat
  w = await clicar(org, 40); // voltar para a lista
  p = proximaJanela(org);
  await org.clickWindow(49, 0, 0);
  await espera(800);
  org.chat('teste-menu');
  w = await Promise.race([p, espera(4000).then(() => null)]);
  w = org.currentWindow;
  listar('Editor da nova conquista', w, 45);

  // Editar o texto falado pelo chat
  await org.clickWindow(13, 0, 0);
  await espera(800);
  org.chat('O teste do menu funcionou');
  await espera(3000);
  console.log('Mensagens:', org.msgs.filter((m) => m.includes('Áudio') || m.includes('Gerando') || m.includes('criada')).join(' | '));

  // 5) Teste só para o admin (Ferramentas -> Testar anúncio)
  org.closeWindow(org.currentWindow);
  await espera(500);
  p = proximaJanela(org);
  org.chat('/sinos');
  await p;
  w = await clicar(org, 16);
  listar('Ferramentas', w, 27);
  w = await clicar(org, 10);
  listar('Escolher conquista', w, 54);
  org.displays = 0;
  b.displays = 0;
  org.msgs.length = 0;
  b.msgs.length = 0;
  await org.clickWindow(0, 0, 0);
  await espera(4000);
  if (org.msgs.some((m) => m.includes('Atualizando o seu pacote'))) {
    console.log('Pacote do admin desatualizado: atualizando e testando de novo');
    await espera(4000);
    p = proximaJanela(org);
    org.chat('/sinos');
    await p;
    await clicar(org, 16);
    await clicar(org, 10);
    await org.clickWindow(0, 0, 0);
    await espera(4000);
  }
  console.log(`Teste só para o admin: sinos vistos pelo Organizador ${org.displays}, por Bot_B ${b.displays}; ` +
    `chat do Bot_B: ${b.msgs.filter((m) => m.includes('[Sino]')).length} mensagens`);

  // 6) Voz: amostra de narrador (Shift + clique)
  p = proximaJanela(org);
  org.chat('/sinos');
  await p;
  w = await clicar(org, 12);
  listar('Voz e narrador', w, 54);

  // Limpeza: apaga a conquista de teste
  org.closeWindow(org.currentWindow);
  org.chat('/sinos lista');
  await espera(1500);
  console.log('Lista:', org.msgs.filter((m) => m.startsWith('- teste-menu')).join(' | ') || '(teste-menu não listada)');

  for (const bot of [org, b]) bot.quit();
  await espera(1000);
  process.exit(0);
}

main().catch((e) => { console.error(e); process.exit(1); });
