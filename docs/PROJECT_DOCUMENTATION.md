# Talk & Play - 채팅 및 게임 프로그램 설계 문서

## 1. 시스템 구성도

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Talk & Play 시스템                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                                서버 (Server)                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                          ChatServer                                  │   │
│  │  - 클라이언트 연결 관리                                              │   │
│  │  - 채팅방 생성/삭제/관리                                             │   │
│  │  - 방 목록 브로드캐스트                                              │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                         │
│              ┌─────────────────────┼─────────────────────┐                  │
│              ▼                     ▼                     ▼                  │
│  ┌───────────────────┐  ┌───────────────────┐  ┌───────────────────┐       │
│  │  ClientHandler    │  │  ClientHandler    │  │  ClientHandler    │       │
│  │  - 메시지 수신    │  │  - 메시지 수신    │  │  - 메시지 수신    │       │
│  │  - 메시지 처리    │  │  - 메시지 처리    │  │  - 메시지 처리    │       │
│  │  - 응답 전송      │  │  - 응답 전송      │  │  - 응답 전송      │       │
│  └───────────────────┘  └───────────────────┘  └───────────────────┘       │
│              │                     │                     │                  │
│              └─────────────────────┼─────────────────────┘                  │
│                                    ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                           ChatRoom                                   │   │
│  │  - 참여자 관리                                                       │   │
│  │  - 채팅 히스토리 저장                                                │   │
│  │  - 메시지 브로드캐스트                                               │   │
│  │  ┌─────────────────────┐  ┌─────────────────────┐                   │   │
│  │  │     OmokGame        │  │     WordGame        │                   │   │
│  │  │  - 15x15 오목판     │  │  - 단어 풀 관리     │                   │   │
│  │  │  - 승패 판정        │  │  - 점수 계산        │                   │   │
│  │  │  - 턴 관리          │  │  - 타이머 관리      │                   │   │
│  │  └─────────────────────┘  └─────────────────────┘                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                              ▲         │
                              │   TCP/IP │
                              │  Socket  │
                              │         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                             클라이언트 (Client)                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                           ChatClient                                 │   │
│  │  - 서버 연결 관리                                                    │   │
│  │  - 메시지 송수신                                                     │   │
│  │  - 리스너 패턴으로 UI에 알림                                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                         │
│              ┌─────────────────────┼─────────────────────┐                  │
│              ▼                     ▼                     ▼                  │
│  ┌───────────────────┐  ┌───────────────────┐  ┌───────────────────┐       │
│  │    LoginPage      │  │   RoomListFrame   │  │    ChatFrame      │       │
│  │  - 닉네임 입력    │  │  - 방 목록 표시   │  │  - 채팅 UI        │       │
│  │  - 서버 연결      │  │  - 방 생성/삭제   │  │  - 파일/이미지    │       │
│  └───────────────────┘  └───────────────────┘  └───────────────────┘       │
│                                                          │                  │
│                              ┌────────────────────────────┤                  │
│                              ▼                           ▼                  │
│                   ┌───────────────────┐       ┌───────────────────┐        │
│                   │   OmokWindow      │       │  WordGameWindow   │        │
│                   │  - 오목판 렌더링  │       │  - 단어 표시      │        │
│                   │  - 클릭 이벤트    │       │  - 입력 UI        │        │
│                   └───────────────────┘       └───────────────────┘        │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 통신 방식
- **프로토콜**: TCP/IP Socket 통신
- **데이터 직렬화**: Java Object Serialization (ObjectInputStream/ObjectOutputStream)
- **메시지 객체**: `Message` 클래스를 통한 직렬화된 객체 전송

---

## 2. 클래스 다이어그램

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              공유 클래스 (chat.shared)                       │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                              Message                                         │
│─────────────────────────────────────────────────────────────────────────────│
│ <<enum>> Type                                                               │
│   LOGIN, ROOM_LIST, CREATE_ROOM, JOIN_ROOM, DELETE_ROOM,                    │
│   CHAT, SYSTEM, ERROR, GAME_EVENT, USER_LIST, IMAGE, FILE, LEAVE_ROOM       │
│                                                                             │
│ <<enum>> GameAction                                                         │
│   REQUEST_JOIN, REQUEST_JOIN_PLAYER, STATE, MOVE, RESULT,                   │
│   RESIGN, RESTART, SUBMIT_WORD, START_WORD_ROUND, LEAVE_GAME, ERROR         │
│                                                                             │
│ <<enum>> GameType                                                           │
│   OMOK, WORD                                                                │
│                                                                             │
│ <<inner class>> WordItem                                                    │
│   - text: String                                                            │
│   - x, y: int                                                               │
│   - answeredBy: String                                                      │
│─────────────────────────────────────────────────────────────────────────────│
│ - type: Type                     - gameAction: GameAction                   │
│ - room: String                   - gameType: GameType                       │
│ - sender: String                 - x, y: int                                │
│ - text: String                   - board: int[][]                           │
│ - rooms: List<String>            - blackPlayer, whitePlayer: String         │
│ - users: List<String>            - currentTurn: String                      │
│ - image: ImageIcon               - finished: boolean                        │
│ - fileName: String               - winner: String                           │
│ - fileData: byte[]               - resultReason: String                     │
│ - fileSize: long                 - spectators: List<String>                 │
│ - wordItems: List<WordItem>      - players: List<String>                    │
│ - scores: Map<String,Integer>    - remainingSeconds: int                    │
│ - running: boolean               - infoText: String                         │
│ - hostPlayer: String                                                        │
│─────────────────────────────────────────────────────────────────────────────│
│ + login(nickname): Message                                                  │
│ + createRoom(roomName): Message                                             │
│ + deleteRoom(roomName): Message                                             │
│ + joinRoom(roomName): Message                                               │
│ + leaveRoom(roomName, name): Message                                        │
│ + chat(room, sender, text): Message                                         │
│ + roomList(rooms): Message                                                  │
│ + system(text): Message                                                     │
│ + error(text): Message                                                      │
│ + systemForRoom(room, text): Message                                        │
│ + userList(room, users): Message                                            │
│ + sendImage(room, sender, image): Message                                   │
│ + sendFile(room, sender, fileName, fileData): Message                       │
│ + gameJoin(room, gameType): Message                                         │
│ + gameJoinPlayer(room): Message                                             │
│ + gameMove(room, x, y): Message                                             │
│ + gameResign(room): Message                                                 │
│ + gameRestart(room): Message                                                │
│ + gameLeave(room): Message                                                  │
│ + gameState(...): Message                                                   │
│ + wordJoin(room): Message                                                   │
│ + wordSubmit(room, word): Message                                           │
│ + wordStartRound(room): Message                                             │
│ + wordState(...): Message                                                   │
│ + wordError(room, text): Message                                            │
│ + gameError(room, text): Message                                            │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                              서버 클래스 (chat.server)                       │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────┐       ┌─────────────────────────────┐
│        ChatServer           │       │      ChatServerMain         │
│─────────────────────────────│       │─────────────────────────────│
│ - clients: Set<ClientHandler>       │ + main(args): void          │
│ - rooms: Map<String,ChatRoom>       └─────────────────────────────┘
│─────────────────────────────│
│ + addClient(client): void   │
│ + removeClient(client): void│
│ + getOrCreateRoom(name): ChatRoom
│ + getRoom(name): ChatRoom   │
│ + deleteRoom(name): boolean │
│ + broadcastRoomListToAll(): void
│ + sendRoomListTo(client): void
│ + broadcastSystem(text): void
└─────────────────────────────┘
              │
              │ 1:N
              ▼
┌─────────────────────────────┐
│      ClientHandler          │
│─────────────────────────────│
│ - socket: Socket            │
│ - server: ChatServer        │
│ - in: ObjectInputStream     │
│ - out: ObjectOutputStream   │
│ - nickname: String          │
│ - joinedRooms: Set<ChatRoom>│
│─────────────────────────────│
│ + run(): void               │
│ + send(msg): void           │
│ - handleMessage(msg): void  │
│ - handleCreateRoom(msg): void
│ - handleDeleteRoom(msg): void
│ - handleJoinRoom(msg): void │
│ - handleLeaveRoom(msg): void│
│ - handleChat(msg): void     │
│ - handleSendImage(msg): void│
│ - handleSendFile(msg): void │
│ - handleGameEvent(msg): void│
│ - handleOmokGame(room,msg): void
│ - handleWordGame(room,msg): void
└─────────────────────────────┘
              │
              │ N:1
              ▼
┌─────────────────────────────┐
│         ChatRoom            │
│─────────────────────────────│
│ - name: String              │
│ - roomName: String          │
│ - participants: Set<ClientHandler>
│ - history: List<Message>    │
│ - currentGame: OmokGame     │
│ - wordGame: WordGame        │
│─────────────────────────────│
│ + join(client): void        │
│ + leave(client): void       │
│ + broadcast(msg, save): void│
│ + broadcastImage(...): void │
│ + broadcastFile(...): void  │
│ + broadcastUserList(): void │
│ + sendHistoryTo(client): void
│ + getOrCreateGame(): OmokGame
│ + getCurrentGame(): OmokGame│
│ + getOrCreateWordGame(): WordGame
│ + getCurrentWordGame(): WordGame
└─────────────────────────────┘
         │               │
         │               │
         ▼               ▼
┌────────────────┐  ┌────────────────┐
│   OmokGame     │  │   WordGame     │
│────────────────│  │────────────────│
│ BOARD_SIZE=15  │  │ ROUND_SECONDS=30
│ - room: ChatRoom │ - room: ChatRoom
│ - board: int[][] │ - players: Set
│ - blackPlayer    │ - spectators: Set
│ - whitePlayer    │ - scores: Map
│ - currentTurn    │ - activeWords: Map
│ - finished       │ - running: boolean
│ - winner         │ - hostPlayer
│ - spectators     │ - wordPool: List
│────────────────│  │────────────────│
│ + joinAsPlayer() │ + joinAsPlayer()
│ + tryJoin()      │ + joinAsSpectator()
│ + placeStone()   │ + startRound()
│ + resign()       │ + submitWord()
│ + restart()      │ + leaveGame()
│ + leaveGame()    │ + onUserLeft()
│ + onUserLeft()   │ + toStateMessage()
│ + toStateMessage()│
└────────────────┘  └────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                             클라이언트 클래스                                │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────┐
│        ChatClient           │
│─────────────────────────────│
│ <<interface>> Listener      │
│   + onMessage(m): void      │
│─────────────────────────────│
│ - socket: Socket            │
│ - in: ObjectInputStream     │
│ - out: ObjectOutputStream   │
│ - nickname: String          │
│ - listeners: List<Listener> │
│─────────────────────────────│
│ + connect(host,port,nick)   │
│ + send(msg): void           │
│ + addListener(l): void      │
│ + removeListener(l): void   │
│ + getNickname(): String     │
│ + disconnect(): void        │
└─────────────────────────────┘
              │
              │ 사용
              ▼
┌─────────────────────────────┐
│        LoginPage            │
│─────────────────────────────│
│ - client: ChatClient        │
│ - nicknameField: JTextField │
│ - hostField: JTextField     │
│ - portField: JTextField     │
│─────────────────────────────│
│ - tryConnect(): void        │
└─────────────────────────────┘
              │
              │ 생성
              ▼
┌─────────────────────────────┐
│      RoomListFrame          │
│─────────────────────────────│
│ - client: ChatClient        │
│ - roomListModel: DefaultListModel
│ - roomList: JList           │
│─────────────────────────────│
│ + requestRoomList(): void   │
│ - createRoom(): void        │
│ - deleteSelectedRoom(): void│
│ - joinSelectedRoom(): void  │
│ - handleServerMessage(m)    │
└─────────────────────────────┘
              │
              │ 생성
              ▼
┌─────────────────────────────┐
│        ChatFrame            │
│─────────────────────────────│
│ - client: ChatClient        │
│ - roomName: String          │
│ - chatPanel: JPanel         │
│ - inputField: JTextField    │
│ - userListModel: DefaultListModel
│ - omokWindow: OmokWindow    │
│ - wordWindow: WordGameWindow│
│─────────────────────────────│
│ - sendCurrentMessage(): void│
│ - selectAndSendFile(): void │
│ - handleServerMessage(m)    │
│ - createMessageBubble()     │
│ - createImageBubble()       │
│ - createFileBubble()        │
│ - showGameSelectionDialog() │
└─────────────────────────────┘
         │               │
         ▼               ▼
┌────────────────┐  ┌────────────────┐  ┌────────────────────┐
│  OmokWindow    │  │WordGameWindow  │  │GameSelectionDialog │
│────────────────│  │────────────────│  │────────────────────│
│ - client       │  │ - client       │  │ - callback         │
│ - roomName     │  │ - roomName     │  │────────────────────│
│ - myNickname   │  │ - myNickname   │  │ (게임 선택 UI)     │
│ - boardPanel   │  │ - wordPanel    │  └────────────────────┘
│ - board: int[][]│ │ - inputField   │
│────────────────│  │ - timerLabel   │
│ + applyState() │  │────────────────│
│ - drawBoard()  │  │ + applyState() │
│ - onClick()    │  │ - submitWord() │
└────────────────┘  └────────────────┘
```

---

## 3. 프로토콜 흐름도

### 3.1 로그인 및 방 목록 조회

```
┌─────────┐                                    ┌─────────┐
│ Client  │                                    │ Server  │
└────┬────┘                                    └────┬────┘
     │                                              │
     │  ──── TCP 연결 수립 ────────────────────────▶│
     │                                              │
     │  LOGIN (nickname="홍길동")                   │
     │  ─────────────────────────────────────────▶ │
     │                                              │
     │                        SYSTEM ("환영합니다") │
     │  ◀───────────────────────────────────────── │
     │                                              │
     │                   ROOM_LIST (rooms=["방1"]) │
     │  ◀───────────────────────────────────────── │
     │                                              │
```

### 3.2 채팅방 생성 및 입장

```
┌─────────┐                                    ┌─────────┐
│ Client  │                                    │ Server  │
└────┬────┘                                    └────┬────┘
     │                                              │
     │  CREATE_ROOM (room="새방")                   │
     │  ─────────────────────────────────────────▶ │
     │                                              │ ChatRoom 생성
     │            ROOM_LIST (rooms=["방1","새방"]) │
     │  ◀───────────────────────────────────────── │ (전체 클라이언트에게)
     │                                              │
     │  JOIN_ROOM (room="새방")                     │
     │  ─────────────────────────────────────────▶ │
     │                                              │ 방 입장 처리
     │               SYSTEM ("홍길동님이 입장...") │
     │  ◀───────────────────────────────────────── │
     │                                              │
     │               USER_LIST (users=["홍길동"])  │
     │  ◀───────────────────────────────────────── │
     │                                              │
     │  [채팅 히스토리가 있으면 전송]               │
     │  ◀───────────────────────────────────────── │
     │                                              │
```

### 3.3 채팅 메시지 전송

```
┌─────────┐                    ┌─────────┐                    ┌─────────┐
│ ClientA │                    │ Server  │                    │ ClientB │
└────┬────┘                    └────┬────┘                    └────┬────┘
     │                              │                              │
     │  CHAT (room, sender, text)   │                              │
     │  ──────────────────────────▶ │                              │
     │                              │  CHAT (room, sender, text)   │
     │  ◀────────────────────────── │ ───────────────────────────▶ │
     │       (본인에게도 브로드캐스트)                               │
     │                              │                              │
```

### 3.4 이미지/파일 전송

```
┌─────────┐                                    ┌─────────┐
│ Client  │                                    │ Server  │
└────┬────┘                                    └────┬────┘
     │                                              │
     │  IMAGE (room, sender, ImageIcon)             │
     │  ─────────────────────────────────────────▶ │
     │                                              │ 방 참여자 전원에게
     │              IMAGE (room, sender, ImageIcon) │ 브로드캐스트
     │  ◀───────────────────────────────────────── │
     │                                              │
     │  FILE (room, sender, fileName, fileData)     │
     │  ─────────────────────────────────────────▶ │
     │                                              │ 방 참여자 전원에게
     │         FILE (room, sender, fileName, data) │ 브로드캐스트
     │  ◀───────────────────────────────────────── │
     │                                              │
```

### 3.5 오목 게임 흐름

```
┌─────────┐                    ┌─────────┐                    ┌─────────┐
│ Player1 │                    │ Server  │                    │ Player2 │
└────┬────┘                    └────┬────┘                    └────┬────┘
     │                              │                              │
     │  GAME_EVENT                  │                              │
     │  (OMOK, REQUEST_JOIN)        │                              │
     │  ──────────────────────────▶ │                              │
     │                              │ Player1 → 흑 플레이어         │
     │         STATE (black=P1)     │                              │
     │  ◀────────────────────────── │ ───────────────────────────▶ │
     │                              │                              │
     │                              │    GAME_EVENT (REQUEST_JOIN) │
     │                              │ ◀─────────────────────────── │
     │                              │ Player2 → 백 플레이어         │
     │     STATE (black=P1,white=P2,│currentTurn=P1)               │
     │  ◀────────────────────────── │ ───────────────────────────▶ │
     │                              │                              │
     │  GAME_EVENT (MOVE, x=7,y=7)  │                              │
     │  ──────────────────────────▶ │                              │
     │                              │ 돌 배치, 턴 전환              │
     │     STATE (board, turn=P2)   │                              │
     │  ◀────────────────────────── │ ───────────────────────────▶ │
     │                              │                              │
     │                              │    GAME_EVENT (MOVE, x,y)    │
     │                              │ ◀─────────────────────────── │
     │     STATE (board, turn=P1)   │                              │
     │  ◀────────────────────────── │ ───────────────────────────▶ │
     │                              │                              │
     │  ... (게임 진행) ...          │                              │
     │                              │                              │
     │  GAME_EVENT (MOVE, x,y)      │                              │
     │  ──────────────────────────▶ │                              │
     │                              │ 5목 완성! 승리 판정           │
     │  STATE (finished=true,       │                              │
     │         winner=P1)           │                              │
     │  ◀────────────────────────── │ ───────────────────────────▶ │
     │                              │                              │
```

### 3.6 단어 맞추기 게임 흐름

```
┌─────────┐                    ┌─────────┐                    ┌─────────┐
│  Host   │                    │ Server  │                    │ Player  │
└────┬────┘                    └────┬────┘                    └────┬────┘
     │                              │                              │
     │  GAME_EVENT                  │                              │
     │  (WORD, REQUEST_JOIN)        │                              │
     │  ──────────────────────────▶ │                              │
     │                              │ Host → 방장 설정              │
     │  STATE (host=Host,players)   │                              │
     │  ◀────────────────────────── │                              │
     │                              │                              │
     │                              │  GAME_EVENT (REQUEST_JOIN)   │
     │                              │ ◀─────────────────────────── │
     │   STATE (players=[Host,P])   │                              │
     │  ◀────────────────────────── │ ───────────────────────────▶ │
     │                              │                              │
     │  GAME_EVENT (START_ROUND)    │                              │
     │  ──────────────────────────▶ │                              │
     │                              │ 단어 배치, 타이머 시작        │
     │  STATE (wordItems, running,  │                              │
     │         remainingSeconds=30) │                              │
     │  ◀────────────────────────── │ ───────────────────────────▶ │
     │                              │                              │
     │                              │  GAME_EVENT (SUBMIT_WORD,    │
     │                              │             text="apple")    │
     │                              │ ◀─────────────────────────── │
     │                              │ 단어 매칭, 점수 +10           │
     │  STATE (wordItems updated,   │                              │
     │         scores updated)      │                              │
     │  ◀────────────────────────── │ ───────────────────────────▶ │
     │                              │                              │
     │  ... (1초마다 STATE 전송) ... │                              │
     │                              │                              │
     │  STATE (running=false,       │ 30초 경과 또는 모든 단어 완료 │
     │         winner="Player")     │                              │
     │  ◀────────────────────────── │ ───────────────────────────▶ │
     │                              │                              │
```

### 3.7 방 퇴장 및 삭제

```
┌─────────┐                                    ┌─────────┐
│ Client  │                                    │ Server  │
└────┬────┘                                    └────┬────┘
     │                                              │
     │  LEAVE_ROOM (room="새방")                    │
     │  ─────────────────────────────────────────▶ │
     │                                              │ 방에서 클라이언트 제거
     │             SYSTEM ("홍길동님이 퇴장...")    │ (방 내 다른 유저에게)
     │                                              │
     │             USER_LIST (users=[...])         │ (방 내 다른 유저에게)
     │                                              │
     │                                              │
     │  DELETE_ROOM (room="새방")                   │
     │  ─────────────────────────────────────────▶ │
     │                                              │ 방 삭제
     │                     SYSTEM ("방이 삭제...")  │ (방 내 유저에게)
     │                                              │
     │                ROOM_LIST (rooms=[...])      │ (전체 클라이언트)
     │  ◀───────────────────────────────────────── │
     │                                              │
```

---

## 4. 프로토콜 목록표

### 4.1 메시지 타입 (Message.Type)

| 타입 | 방향 | 설명 | 주요 필드 |
|------|------|------|-----------|
| `LOGIN` | C→S | 클라이언트 로그인 요청 | `sender` (닉네임) |
| `ROOM_LIST` | S→C | 채팅방 목록 전송 | `rooms` (방 이름 리스트) |
| `CREATE_ROOM` | C→S | 새 채팅방 생성 요청 | `room` (방 이름) |
| `DELETE_ROOM` | C→S | 채팅방 삭제 요청 | `room` (방 이름) |
| `JOIN_ROOM` | C→S | 채팅방 입장 요청 | `room` (방 이름) |
| `LEAVE_ROOM` | C→S | 채팅방 퇴장 요청 | `room`, `sender` |
| `CHAT` | C↔S | 채팅 메시지 | `room`, `sender`, `text` |
| `SYSTEM` | S→C | 시스템 알림 메시지 | `room` (선택), `text` |
| `ERROR` | S→C | 오류 메시지 | `text` |
| `USER_LIST` | S→C | 방 참여자 목록 | `room`, `users` |
| `IMAGE` | C↔S | 이미지 전송 | `room`, `sender`, `image` |
| `FILE` | C↔S | 파일 전송 | `room`, `sender`, `fileName`, `fileData`, `fileSize` |
| `GAME_EVENT` | C↔S | 게임 이벤트 | `room`, `gameType`, `gameAction`, ... |

### 4.2 게임 액션 (Message.GameAction)

| 액션 | 방향 | 설명 | 관련 필드 |
|------|------|------|-----------|
| `REQUEST_JOIN` | C→S | 게임 참여 요청 (자동 배정) | `room`, `gameType` |
| `REQUEST_JOIN_PLAYER` | C→S | 플레이어로 참여 요청 | `room` |
| `STATE` | S→C | 게임 상태 동기화 | 모든 게임 상태 필드 |
| `MOVE` | C→S | 오목 돌 놓기 | `room`, `x`, `y` |
| `RESIGN` | C→S | 오목 기권 | `room` |
| `RESTART` | C→S | 오목 다시하기 | `room` |
| `LEAVE_GAME` | C→S | 게임만 나가기 (방 유지) | `room` |
| `SUBMIT_WORD` | C→S | 단어 제출 | `room`, `text` |
| `START_WORD_ROUND` | C→S | 단어게임 라운드 시작 | `room` |
| `ERROR` | S→C | 게임 오류 | `room`, `text` |

### 4.3 게임 타입 (Message.GameType)

| 타입 | 설명 |
|------|------|
| `OMOK` | 오목 게임 |
| `WORD` | 단어 맞추기 게임 |

### 4.4 오목 게임 상태 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `board` | `int[][]` | 15x15 바둑판 (0:빈칸, 1:흑, 2:백) |
| `blackPlayer` | `String` | 흑돌 플레이어 닉네임 |
| `whitePlayer` | `String` | 백돌 플레이어 닉네임 |
| `currentTurn` | `String` | 현재 차례 플레이어 닉네임 |
| `finished` | `boolean` | 게임 종료 여부 |
| `winner` | `String` | 승자 닉네임 (무승부 시 null) |
| `resultReason` | `String` | 결과 사유 ("WIN", "RESIGN", "DRAW") |
| `spectators` | `List<String>` | 관전자 닉네임 목록 |

### 4.5 단어 맞추기 게임 상태 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `wordItems` | `List<WordItem>` | 단어 목록 (텍스트, 좌표, 맞춘 사람) |
| `players` | `List<String>` | 플레이어 닉네임 목록 (최대 4명) |
| `spectators` | `List<String>` | 관전자 닉네임 목록 |
| `scores` | `Map<String,Integer>` | 플레이어별 점수 |
| `running` | `boolean` | 라운드 진행 중 여부 |
| `remainingSeconds` | `int` | 남은 시간 (초) |
| `winner` | `String` | 최고점자 닉네임 |
| `infoText` | `String` | 상태 안내 텍스트 |
| `hostPlayer` | `String` | 방장 닉네임 |

### 4.6 메시지 생성 메서드 요약

```java
// 기본 메시지
Message.login(nickname)                    // 로그인
Message.createRoom(roomName)               // 방 생성
Message.deleteRoom(roomName)               // 방 삭제
Message.joinRoom(roomName)                 // 방 입장
Message.leaveRoom(roomName, nickname)      // 방 퇴장
Message.chat(room, sender, text)           // 채팅
Message.roomList(rooms)                    // 방 목록
Message.userList(room, users)              // 유저 목록
Message.system(text)                       // 시스템 메시지
Message.systemForRoom(room, text)          // 방 시스템 메시지
Message.error(text)                        // 에러 메시지

// 파일/이미지
Message.sendImage(room, sender, image)     // 이미지 전송
Message.sendFile(room, sender, name, data) // 파일 전송

// 오목 게임
Message.gameJoin(room, gameType)           // 게임 참여
Message.gameJoinPlayer(room)               // 플레이어 참여
Message.gameMove(room, x, y)               // 돌 놓기
Message.gameResign(room)                   // 기권
Message.gameRestart(room)                  // 다시하기
Message.gameLeave(room)                    // 게임 나가기
Message.gameState(...)                     // 상태 동기화
Message.gameError(room, text)              // 게임 에러

// 단어 게임
Message.wordJoin(room)                     // 단어게임 참여
Message.wordSubmit(room, word)             // 단어 제출
Message.wordStartRound(room)               // 라운드 시작
Message.wordState(...)                     // 상태 동기화
Message.wordError(room, text)              // 단어게임 에러
```

---

## 5. 주요 기능 요약

### 5.1 채팅 기능
- 다중 채팅방 동시 참여 지원
- 텍스트 메시지 (버블 형태 UI)
- 이미지 전송 (JPG, PNG, GIF)
- 파일 전송 (PDF, TXT, ZIP 등 - 최대 10MB)
- 채팅 히스토리 저장 및 신규 입장자에게 전송

### 5.2 오목 게임
- 15x15 바둑판
- 2인 플레이어 + 다수 관전자
- 자동 플레이어/관전자 배정
- 승리/무승부/기권 판정
- 게임 다시하기 기능

### 5.3 단어 맞추기 게임
- 최대 4인 플레이어
- 30초 제한시간
- 25~30개 단어 무작위 배치
- 실시간 점수 계산 (+10점/단어)
- 방장이 라운드 시작

---

## 6. 개발 환경

- **언어**: Java 17+
- **GUI**: Java Swing
- **통신**: TCP Socket (ObjectStream)
- **동시성**: ConcurrentHashMap, CopyOnWriteArrayList, synchronized
- **빌드**: Gradle
