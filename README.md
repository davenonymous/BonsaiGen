![Bonsai Gen](https://github.com/thraaawn/BonsaiTrees/blob/1.18.1/assets/readme-logo.png?raw=true)

# Bonsai Gen

A Minecraft mod used to create mod integration for Bonsai Trees 4.

There are two ways to include support for a mod:


## Pull-Requests / Using the game test framework

This is the recommended way to add support for a mod as it includes the mod in the next Bonsai
Trees 4 release automatically. It requires you to edit some files and know how to create forks
and pull requests on GitHub.

The game test framework is a system that allows you to run tests in a short-lived server instance
of the game. It is (ab)used by Bonsai Trees 4 to generate the data and resource packs for all
loaded mods. Work has been done to ensure that most mods work out of the box with this system,
but there are some mods that require manual intervention and require opening an issue on the
Bonsai Trees 4 GitHub repository.

If we managed to set up everything correctly on GitHub it should be enough to create a pull
request with the mod added to the `mods.conf` file. The rest should be handled automatically.


### Example

**Step 0**: Get the project name, project id and file id of the mod you want to add.
You can find this information on the CurseForge page of the mod. [Where?](assets/curseforge_file_data.png)

**Step 1**: Fork the `1.21.1` branch of the [BonsaiGen repository](https://github.com/davenonymous/BonsaiGen)

**Step 2**: Edit the `mods.conf` file and add the mod to the end of the list

```
cobblemon                                687131       6125026      # 2025-01-25T23:48:46.807Z  | Cobblemon-neoforge-1.6.1+1.21.1.jar
pagans-blessing                          952071       5889348      # 2024-11-09T22:33:33.213Z  | paganbless-0.2.5.jar
modonomicon                              538392       6543721      # 2025-05-18T15:33:45.83Z   | modonomicon-1.21.1-neoforge-1.114.2.jar
valhelsia-core                           416935       6296775      # 2025-03-12T17:29:00.17Z   | valhelsia_core-neoforge-1.21.1-1.1.5.jar
smartbrainlib                            661293       6101242      # 2025-01-18T23:01:30.52Z   | SmartBrainLib-neoforge-1.21.1-1.16.7.jar
kotlin-for-forge                         351264       6497906      # 2025-05-05T07:11:02.707Z  | kotlinforforge-5.8.0-all.jar

<mod-project-name>                       <mod-id>     <file-id>    # comment
```

**Step 3**: Create a Pull Request with your changes. GitHub should automatically run a test server
instance and generate the data and resource packs for the mod you added. You can check the changes
after a few minutes in the Pull Request.

**Step 4**: The Bonsai Trees 4 team will review your pull request and merge it if everything is
correct. If there are any issues with the mod or the generated data packs, we will comment on
the pull request and ask you to fix them.


## In-Game

If you don't want to create a pull request or the mod you want to add is not available via Maven you
can use an in-game command to generate the data and resource packs for the mod you want to add.

If you want to automatically generate zip files for the packs or export them to a specific path you
can tweak some settings in the Pack Generation config available in the mod options or config file.

**Step 1**: Start a new superflat world with both mods (bonsai trees and yours) installed.

**Step 2**: Run the command `/bonsaigen generate data-pack <modid>`, e.g. `/bonsaigen
generate data-pack twilightforest`.

**Step 3**: You can find the generated data and resource packs in your Minecraft instance folder
under `bonsai-generated/` or in the path you specified in the config.

**Step 4**: Add the generated data and resource packs to your mod pack or corresponding instance folders.
Make sure they are being loaded!

You can also specify `--all` as mod id to generate data and resource packs for all loaded mods.

## Influencing the Generation

Sometimes trees don't generate correctly or look weird and you want to tweak the generation a bit.
Common issues include root blocks being generated below the tree line or the sapling requiring a
specific soil block or medium to grow on.

There are two more data maps you can use to influence the generation of the tree model and the
details of the bonsais. These are the `fixed_tree_generation.json` and `bonsai_generation.json`
data maps.

- The `fixed_tree_generation.json` data map is used to modify the generated model.
- The `bonsai_generation.json` data map is used to modify the bonsais, e.g. their valid soil types.

### Modifying the Model

You can create a `fixed_tree_generation.json` file in the `data/bonsaigen/data_maps/item` folder
for your mod. There are few options you can tweak:

- `yCutOff`: The y offset at which the tree should be cut off. This is useful for trees that
  have a long stem, lots of foliage or roots below the tree line.
- `seed`: The seed used to generate the tree. While the seed used for tree generation is
  deterministic, it is not always a pretty representation of the tree. You can use this option to
  force a specific seed for the tree generation.
- `preferredFeature`: One of Tree, SecondaryTree, MegaTree, SecondaryMegaTree, Flowers or
  SecondaryFlowers. This is used to force a specific feature to be used for the tree generation.
  E.g. the default jungle tree does not look like a jungle tree, which is why we use the MegaTree
  feature for the jungle tree. This is not always necessary, but it can be useful for some trees.
- `preferredSoil`: The soil block used to grow the tree. This is useful for trees that require a
  different block than dirt/grass to grow on.
- `preferredMedium`: Some plants/trees/corals only grow in water. While 99% of trees use air as
  the medium, you can use this option to force a specific medium for the tree generation. The region
  in the world where the tree is generated will be filled with the specified medium before the tree
  is generated.

#### Example for Twilight Forest
```json
{
    "neoforge:conditions": [
        {
            "type": "neoforge:mod_loaded",
            "modid": "twilightforest"
        }
    ],
    "replace": false,
    "values": {
        "twilightforest:time_sapling": {
            "yCutOff": 18
        },
        "twilightforest:mangrove_sapling": {
            "yCutOff": 3
        },
        "twilightforest:canopy_sapling": {
            "yCutOff": 9
        }
    }
}
```
