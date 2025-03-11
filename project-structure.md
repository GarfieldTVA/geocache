# Structure du Projet

```
hide-and-seek/
├── client/                  # Frontend React
│   ├── public/
│   └── src/
│       ├── components/      # Composants UI réutilisables
│       ├── contexts/        # Contextes React (auth, game, etc.)
│       ├── pages/           # Pages principales
│       ├── hooks/           # Custom hooks
│       ├── services/        # Services API
│       ├── utils/           # Fonctions utilitaires
│       └── assets/          # Images, animations, etc.
├── server/                  # Backend Node.js
│   ├── controllers/         # Gestionnaires de routes
│   ├── models/              # Modèles de données
│   ├── routes/              # Définitions des routes API
│   ├── services/            # Logique métier
│   ├── sockets/             # Gestionnaires de websockets
│   └── utils/               # Fonctions utilitaires
├── shared/                  # Code partagé entre client et serveur
│   └── types/               # TypeScript types/interfaces
└── .env                     # Variables d'environnement
```
