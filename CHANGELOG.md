# Changelog

## v2.7 (2026-08-19)

### 🐛 Bug Fixes — Alarmes

- **Double-advance em checklist** — Itens com `alertType==2` (notificação + alarme) e recorrência avançavam duas vezes, pulando uma ocorrência. Adicionada guarda `reminderTime <= now` antes de reagendar.
- **Alarmes em notas da lixeira** — `moveToTrash()` agora cancela alarmes pendentes no AlarmManager antes de mover.
- **Alarmes em clearTrash** — Cancela alarmes de todas as notas antes de deletar permanentemente.
- **Alarmes em performFullReset** — Cancela todos os alarmes antes de resetar o banco.
- **Alarmes em doRestore** — Notas restauradas de backup agora recebem alarmes agendados.
- **Alarmes em restoreNote** — Restaurar nota da lixeira reagenda alarmes futuros.
- **convertToChecklist sobrescrito** — `onPause()` auto-save revertera a conversão. Agora `saved=true` impede o sobrescrevimento.

### 🐛 Bug Fixes — Dados

- **Widget toggleChecklistItem** — Toggle de itens no widget não sincronizava com a tabela `checklist_items`. Agora reescreve a tabela ao alterar.
- **SettingsManager.getTheme() hardcoded** — Retornava sempre `1` (dark). Agora lê do SharedPreferences.

### ♻️ Arquitetura

- **Room Database** — Adicionada camada Room com Entity/DAO para `notes`, `checklist_items` e `attachments`. `NoteRepositoryImpl` agora usa Room com fallback legacy.
- **NoteShareManager** — Lógica de compartilhamento, importação e exportação extraída do MainActivity (~150 linhas) para classe dedicada.
- **Base64 migration** — `android.util.Base64` → `java.util.Base64` (via desugaring), permitindo testes unitários.

### ⚡ Ciclo de Vida Otimizado

- **SecurityManager** — `getEncryptedPrefs()` cacheada (evita recriar `EncryptedSharedPreferences` a cada chamada).
- **SecurityDataStore** — `CompositeDisposable.dispose()` adicionado para evitar leak de RxJava subscriptions.
- **SettingsManager** — `clearObservers()` adicionado; observers removidos no `onDestroy` das activities.
- **NoteViewModel** — `onCleared()` agora fecha a conexão `DatabaseHelper`.
- **MainActivity** — `searchHandler.removeCallbacksAndMessages(null)` no `onDestroy`.
- **EditActivity/ChecklistActivity** — `OnGlobalLayoutListener` removido no `onDestroy` (evita activity leak).

### 🧪 Testes

- **35 testes unitários** (todos passando) com Robolectric 4.16.1:
  - `SecurityHelperTest` — 7 testes de criptografia AES-GCM round-trip
  - `NoteShareManagerTest` — 11 testes de formatação, sanitize, Intent creation e decrypt
  - `NoteRepositoryImplTest` — 17 testes de Room integration (insert, update, delete, search, filters, trash, entity conversion)
- **69 testes instrumentados** (on-device, `app/src/androidTest/`): `FullFunctionalTest` (14), `WidgetFunctionalTest` (14), `AttachmentFunctionalTest` (9), `SecurityFunctionalTest` (11), `AlarmActivityFunctionalTest` (7), `SearchFilterFunctionalTest` (9), `EdgeCaseFunctionalTest` (5).

### 🐛 Bug Fixes — Bateria B12–B17

- **Widget apagava agendamentos de itens** — o toggle de checklist recriava a tabela com IDs novos. Agora atualiza a linha in-place (preserva `reminder_time`, `alert_type`, recorrência e IDs de alarme).
- **Widget derrubava o app com cor inválida** — `note.color` fora da faixa estourava `ArrayIndexOutOfBoundsException`; adicionado clamp.
- **Excluir um anexo apagava a pasta inteira da nota** — novo `AttachmentManager.deleteAttachmentFile()`.
- **Arquivos de anexo vazavam** em exclusão definitiva, limpeza da lixeira e reset total; cascata adicionada ao `NoteViewModel`.
- **Anotação de desenho salva em coordenadas erradas** — `ImageViewerActivity` agora inverte a matriz da imagem.
- **Notas trancadas vazavam** título/ciphertext/nome de item em notificações, alarme full-screen e `AlarmActivity`; tudo mascarado e edição bloqueada.
- **Alarmes não eram cancelados** no reset total, na lixeira e na exclusão definitiva; restaurar agora reagenda itens também.
- **Edições em checklist trancada eram perdidas** ao destrancar; `syncChecklistItemsTable()` preserva IDs e agendamentos.
- **Toggle de item no alarme não sincronizava** `checklist_items` (editor/widget ficavam stale).
- **Busca tratava `%` e `_` como curingas**; agora são literais.
- **Busca com acentos era case-sensitive**; consultas não-ASCII usam `toLowerCase(Locale.ROOT)`.
- **Room não é mais o backend de produção** — `NoteRepositoryImpl(Context)` usa exclusivamente `DatabaseHelper`; Room permanece só nos testes unitários (in-memory).

### 📝 Documentação

- **README.md** — Documentação completa com arquitetura, funcionalidades, instruções de build e estrutura do banco de dados.

### 📦 Dependências Adicionadas

- `androidx.room:room-runtime:2.6.1`
- `androidx.room:room-compiler:2.6.1`
- `org.robolectric:robolectric:4.16.1`

---

## v2.6

- Versão anterior (release estable).
