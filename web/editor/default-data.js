// Snapshot of QuestPlus src/main/resources/defaults/ (Minecraft 26.3).
// Keep in sync with shipped definitions. Source: main/defaults/*.json.
export const BUILTIN_PROJECT = {
  "categories": [
    {
      "id": "questplus:combat",
      "icon": "minecraft:iron_sword",
      "title": {
        "en_us": "Combat",
        "fr_fr": "Combat"
      },
      "description": {
        "en_us": "Face hostile mobs and dangerous bosses.",
        "fr_fr": "Affrontez les créatures hostiles et les boss dangereux."
      }
    },
    {
      "id": "questplus:exploration",
      "icon": "minecraft:compass",
      "title": {
        "en_us": "Exploration",
        "fr_fr": "Exploration"
      },
      "description": {
        "en_us": "Find valuable discoveries on your travels.",
        "fr_fr": "Faites de précieuses découvertes au fil de vos voyages."
      }
    },
    {
      "id": "questplus:mining",
      "icon": "minecraft:iron_pickaxe",
      "title": {
        "en_us": "Mining",
        "fr_fr": "Minage"
      },
      "description": {
        "en_us": "Gather stone and ore with care.",
        "fr_fr": "Récoltez la pierre et le minerai avec méthode."
      }
    },
    {
      "id": "questplus:survival",
      "icon": "minecraft:oak_log",
      "title": {
        "en_us": "Survival",
        "fr_fr": "Survie"
      },
      "description": {
        "en_us": "Practical work for life in the Overworld.",
        "fr_fr": "Des travaux pratiques pour vivre dans la Surface."
      }
    }
  ],
  "quests": [
    {
      "schemaVersion": 2,
      "id": "questplus:combat/iron_and_zombie",
      "category": "questplus:combat",
      "icon": "minecraft:iron_ingot",
      "title": {
        "en_us": "Armed for Trouble",
        "fr_fr": "Prêt au combat"
      },
      "description": {
        "en_us": "Carry 64 iron ingots and defeat a zombie.",
        "fr_fr": "Ayez 64 lingots de fer et vainquez un zombie."
      },
      "objectives": [
        {
          "id": "iron",
          "event": "inventory_count",
          "target": "minecraft:iron_ingot",
          "count": 64
        },
        {
          "id": "zombie",
          "event": "entity_kill",
          "target": "minecraft:zombie",
          "count": 1
        }
      ],
      "rewards": [
        {
          "type": "experience",
          "amount": 75
        }
      ],
      "progression": "once"
    },
    {
      "schemaVersion": 2,
      "id": "questplus:combat/skeleton_repeat",
      "category": "questplus:combat",
      "icon": "minecraft:bone",
      "title": {
        "en_us": "Skeleton Patrol",
        "fr_fr": "Patrouille de squelettes"
      },
      "description": {
        "en_us": "Defeat 100 skeletons, then restart after the cooldown.",
        "fr_fr": "Vainquez 100 squelettes, puis recommencez après le délai."
      },
      "objectives": [
        {
          "id": "skeletons",
          "event": "entity_kill",
          "target": "minecraft:skeleton",
          "count": 100
        }
      ],
      "rewards": [
        {
          "type": "experience",
          "amount": 100,
          "text": {
            "en_us": "100 experience",
            "fr_fr": "100 points d'expérience"
          }
        }
      ],
      "progression": "repeatable",
      "cooldownSeconds": 100
    },
    {
      "schemaVersion": 1,
      "id": "questplus:combat/skeletons",
      "category": "questplus:combat",
      "icon": "minecraft:bone",
      "title": {
        "en_us": "Keep Your Distance",
        "fr_fr": "Gardez vos distances"
      },
      "description": {
        "en_us": "Deal with the skeletons harassing the road.",
        "fr_fr": "Débarrassez la route des squelettes qui la menacent."
      },
      "objectives": [
        {
          "id": "skeletons",
          "event": "entity_kill",
          "target": "minecraft:skeleton",
          "count": 5,
          "text": {
            "en_us": "Defeat 5 skeletons",
            "fr_fr": "Vainquez 5 squelettes"
          }
        }
      ],
      "rewards": [
        {
          "type": "experience",
          "amount": 100
        },
        {
          "type": "item",
          "item": "minecraft:arrow",
          "count": 16
        }
      ],
      "progression": "once"
    },
    {
      "schemaVersion": 1,
      "id": "questplus:combat/wither",
      "category": "questplus:combat",
      "icon": "minecraft:nether_star",
      "title": {
        "en_us": "A Narrow Victory",
        "fr_fr": "Une victoire de justesse"
      },
      "description": {
        "en_us": "Defeat the Wither while you are down to two health points or less.",
        "fr_fr": "Vainquez le Wither alors qu'il ne vous reste que deux points de vie ou moins."
      },
      "objectives": [
        {
          "id": "wither",
          "event": "entity_kill",
          "target": "minecraft:wither",
          "count": 1,
          "text": {
            "en_us": "Defeat the Wither at 2 health or less",
            "fr_fr": "Vainquez le Wither avec 2 points de vie ou moins"
          },
          "conditions": {
            "healthAtMost": 2
          }
        }
      ],
      "requires": {
        "and": [
          {
            "quest": "questplus:combat/skeletons"
          },
          {
            "not": {
              "quest": "questplus:mining/iron_ore"
            }
          }
        ]
      },
      "rewards": [
        {
          "type": "experience",
          "amount": 500
        },
        {
          "type": "item",
          "item": "minecraft:nether_star",
          "count": 1
        }
      ],
      "progression": "once",
      "narrative": {
        "en_us": "Sometimes survival is measured by a single heart.",
        "fr_fr": "Parfois, la survie se joue à un seul cœur."
      }
    },
    {
      "schemaVersion": 1,
      "id": "questplus:exploration/diamond",
      "category": "questplus:exploration",
      "icon": "minecraft:diamond",
      "title": {
        "en_us": "A Bright Find",
        "fr_fr": "Une trouvaille brillante"
      },
      "description": {
        "en_us": "Bring a diamond home from your travels underground.",
        "fr_fr": "Rapportez un diamant de votre expédition souterraine."
      },
      "objectives": [
        {
          "id": "diamond",
          "event": "item_obtained",
          "target": "minecraft:diamond",
          "count": 1,
          "text": {
            "en_us": "Obtain 1 diamond",
            "fr_fr": "Obtenez 1 diamant"
          }
        }
      ],
      "requires": {
        "or": [
          {
            "quest": "questplus:mining/iron_ore"
          },
          {
            "quest": "questplus:combat/skeletons"
          }
        ]
      },
      "rewards": [
        {
          "type": "experience",
          "amount": 150
        },
        {
          "type": "item",
          "item": "minecraft:torch",
          "count": 16
        }
      ],
      "progression": "once"
    },
    {
      "schemaVersion": 2,
      "id": "questplus:exploration/three_realms",
      "category": "questplus:exploration",
      "icon": "minecraft:compass",
      "title": {
        "en_us": "Three Realms",
        "fr_fr": "Trois dimensions"
      },
      "description": {
        "en_us": "Visit a plains biome, enter the Nether, then travel 1,000 blocks.",
        "fr_fr": "Visitez une plaine, entrez dans le Nether, puis parcourez 1 000 blocs."
      },
      "objectives": [
        {
          "id": "plains",
          "event": "biome_visit",
          "target": "minecraft:plains",
          "count": 1
        },
        {
          "id": "nether",
          "event": "dimension_enter",
          "target": "minecraft:the_nether",
          "count": 1
        },
        {
          "id": "travel",
          "event": "travel_distance",
          "target": "questplus:travel",
          "count": 1000
        }
      ],
      "rewards": [
        {
          "type": "item",
          "item": "minecraft:compass",
          "count": 1
        }
      ],
      "progression": "once",
      "objectiveMode": "all"
    },
    {
      "schemaVersion": 1,
      "id": "questplus:mining/iron_ore",
      "category": "questplus:mining",
      "icon": "minecraft:iron_pickaxe",
      "title": {
        "en_us": "Iron Prospector",
        "fr_fr": "Prospecteur de fer"
      },
      "description": {
        "en_us": "Follow your first supplies into the deeper veins.",
        "fr_fr": "Après vos premières réserves, partez vers des filons plus profonds."
      },
      "objectives": [
        {
          "id": "iron_ore",
          "event": "block_break",
          "target": "#minecraft:iron_ores",
          "count": 32,
          "text": {
            "en_us": "Break 32 iron ores",
            "fr_fr": "Cassez 32 minerais de fer"
          },
          "conditions": {
            "and": [
              {
                "dimension": "minecraft:overworld"
              },
              {
                "or": [
                  {
                    "tool": "minecraft:iron_pickaxe"
                  },
                  {
                    "tool": "minecraft:diamond_pickaxe"
                  }
                ]
              }
            ]
          }
        }
      ],
      "requires": {
        "and": [
          {
            "quest": "questplus:survival/logs"
          },
          {
            "quest": "questplus:mining/stone"
          }
        ]
      },
      "rewards": [
        {
          "type": "experience",
          "amount": 180
        },
        {
          "type": "item",
          "item": "minecraft:iron_ingot",
          "count": 8
        }
      ],
      "progression": "once"
    },
    {
      "schemaVersion": 1,
      "id": "questplus:mining/stone",
      "category": "questplus:mining",
      "icon": "minecraft:stone",
      "title": {
        "en_us": "Solid Foundations",
        "fr_fr": "Des bases solides"
      },
      "description": {
        "en_us": "Gather enough stone to build a lasting base.",
        "fr_fr": "Rassemblez assez de pierre pour bâtir une base durable."
      },
      "objectives": [
        {
          "id": "stone",
          "event": "block_break",
          "target": "minecraft:stone",
          "count": 64,
          "text": {
            "en_us": "Break 64 stone",
            "fr_fr": "Cassez 64 blocs de pierre"
          }
        }
      ],
      "rewards": [
        {
          "type": "experience",
          "amount": 80
        },
        {
          "type": "item",
          "item": "minecraft:iron_pickaxe",
          "count": 1
        }
      ],
      "progression": "once"
    },
    {
      "schemaVersion": 2,
      "id": "questplus:mining/stone_infinite",
      "category": "questplus:mining",
      "icon": "minecraft:stone",
      "title": {
        "en_us": "Stone Hauler",
        "fr_fr": "Transporteur de pierre"
      },
      "description": {
        "en_us": "Mine 64 stone for each reward cycle.",
        "fr_fr": "Minez 64 pierres à chaque cycle de récompense."
      },
      "objectives": [
        {
          "id": "stone",
          "event": "block_break",
          "target": "minecraft:stone",
          "count": 64
        }
      ],
      "rewards": [
        {
          "type": "item",
          "item": "minecraft:torch",
          "count": 16
        }
      ],
      "progression": "infinite"
    },
    {
      "schemaVersion": 2,
      "id": "questplus:mining/stone_milestones",
      "category": "questplus:mining",
      "icon": "minecraft:stone_pickaxe",
      "title": {
        "en_us": "Stone Milestones",
        "fr_fr": "Jalons de pierre"
      },
      "description": {
        "en_us": "Reach 100, 1,000 and 10,000 stone mined.",
        "fr_fr": "Atteignez 100, 1 000 et 10 000 pierres minées."
      },
      "objectives": [
        {
          "id": "stone",
          "event": "block_break",
          "target": "minecraft:stone",
          "count": 100
        }
      ],
      "rewards": [],
      "progression": "tiered",
      "tiers": [
        {
          "id": "hundred",
          "threshold": 1,
          "rewards": [
            {
              "type": "experience",
              "amount": 10
            }
          ]
        },
        {
          "id": "thousand",
          "threshold": 10,
          "rewards": [
            {
              "type": "experience",
              "amount": 50
            }
          ]
        },
        {
          "id": "ten_thousand",
          "threshold": 100,
          "rewards": [
            {
              "type": "experience",
              "amount": 200
            }
          ]
        }
      ]
    },
    {
      "schemaVersion": 2,
      "id": "questplus:survival/composite_sequence",
      "category": "questplus:survival",
      "icon": "minecraft:oak_log",
      "title": {
        "en_us": "Shelter in Order",
        "fr_fr": "Abri, dans l'ordre"
      },
      "description": {
        "en_us": "Break 3 oak logs, then 8 stone.",
        "fr_fr": "Cassez 3 bûches de chêne, puis 8 pierres."
      },
      "objectives": [
        {
          "id": "logs",
          "event": "block_break",
          "target": "minecraft:oak_log",
          "count": 3
        },
        {
          "id": "stone",
          "event": "block_break",
          "target": "minecraft:stone",
          "count": 8
        }
      ],
      "rewards": [
        {
          "type": "item",
          "item": "minecraft:bread",
          "count": 4
        }
      ],
      "progression": "once",
      "objectiveMode": "sequence"
    },
    {
      "schemaVersion": 1,
      "id": "questplus:survival/logs",
      "category": "questplus:survival",
      "icon": "minecraft:oak_log",
      "title": {
        "en_us": "First Timber",
        "fr_fr": "Premières bûches"
      },
      "description": {
        "en_us": "Collect wood for your first camp.",
        "fr_fr": "Récoltez du bois pour votre premier camp."
      },
      "objectives": [
        {
          "id": "logs",
          "event": "block_break",
          "target": "#minecraft:logs",
          "count": 16,
          "text": {
            "en_us": "Break 16 logs",
            "fr_fr": "Cassez 16 bûches"
          },
          "conditions": {
            "not": {
              "sneaking": true
            }
          }
        }
      ],
      "rewards": [
        {
          "type": "experience",
          "amount": 40
        },
        {
          "type": "item",
          "item": "minecraft:bread",
          "count": 4
        }
      ],
      "progression": "once",
      "narrative": {
        "en_us": "A sturdy shelter begins with a handful of logs.",
        "fr_fr": "Un abri solide commence par quelques bûches."
      }
    },
    {
      "schemaVersion": 2,
      "kind": "information",
      "acknowledgement": "manual",
      "id": "questplus:survival/welcome",
      "category": "questplus:survival",
      "icon": "minecraft:book",
      "title": {
        "en_us": "Welcome to Quest Plus",
        "fr_fr": "Bienvenue sur Quest Plus"
      },
      "description": {
        "en_us": "Learn how the quest system works before starting your adventure.",
        "fr_fr": "Découvrez le fonctionnement des quêtes avant de commencer votre aventure."
      },
      "narrative": {
        "en_us": "Some quests are independent, others need prerequisites. Complete objectives to earn rewards. Teams share progress retroactively. Acknowledge this page to mark it as read.",
        "fr_fr": "Certaines quêtes sont indépendantes, d'autres nécessitent des prérequis. Terminez les objectifs pour obtenir des récompenses. Les équipes partagent rétroactivement leur progression. Validez cette page pour indiquer sa lecture."
      },
      "objectives": [],
      "rewards": [],
      "progression": "once"
    }
  ],
  "config": {
    "schemaVersion": 1,
    "autosaveSeconds": 5
  }
};
