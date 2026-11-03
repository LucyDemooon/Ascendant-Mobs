"""Recipes, tags, advancements, models, client item definitions, sounds.json, the icon font and pack.mcmeta,
per version profile.
"""
from __future__ import annotations

import icons
import sounds
from lang import ADVANCEMENTS

NS = 'remnant_mobs'
DESCRIPTION = 'Remnants: RPG Mobs - monsters with levels, ranks and traits'


class Profile:
    def __init__(self, recipes, items_json, plural_dirs, icon_key, background, pack):
        self.recipes = recipes            # 'object' (1.20.1/1.21.1 ingredient objects) | 'string' (26.x)
        self.items_json = items_json      # True: write assets/<ns>/items/*.json (1.21.4+)
        self.plural_dirs = plural_dirs    # True for 1.20.1 (recipes/, advancements/, tags/entity_types/)
        self.icon_key = icon_key          # 'item' (1.20.1) or 'id'
        self.background = background      # advancement background id
        self.pack = pack                  # pack.mcmeta "pack" object
        self.result_key = 'item' if plural_dirs else 'id'

    def dir(self, kind):
        if self.plural_dirs:
            return {'recipe': 'recipes', 'advancement': 'advancements', 'tags/item': 'tags/items',
                    'tags/entity_type': 'tags/entity_types'}.get(kind, kind)
        return kind


_BG_NEW = f'{NS}:gui/advancements/backgrounds/remnants'
_BG_OLD = f'{NS}:textures/gui/advancements/backgrounds/remnants.png'
PROFILES = {
    '26.3': Profile('string', True, False, 'id', _BG_NEW, {'description': DESCRIPTION, 'min_format': 121, 'max_format': 121}),
    '26.2': Profile('string', True, False, 'id', _BG_NEW, {'pack_format': 34, 'description': DESCRIPTION}),
    '26.1.2': Profile('string', True, False, 'id', _BG_NEW, {'pack_format': 34, 'description': DESCRIPTION}),
    '1.21.1': Profile('object', False, False, 'id', _BG_OLD, {'pack_format': 34, 'description': DESCRIPTION}),
    '1.20.1': Profile('object', False, True, 'item', _BG_OLD, {'pack_format': 15, 'description': DESCRIPTION}),
}

ITEMS = ['remnant_shard', 'hunters_lens', 'brand_of_challenge', 'vital_ember', 'mending_salve', 'hunters_draught']


def _ing(value, profile):
    if profile.recipes == 'string':
        return value
    return {'tag': value[1:]} if value.startswith('#') else {'item': value}


def recipes(profile):
    def shaped(pattern, key, result, category):
        return {'type': 'minecraft:crafting_shaped', 'category': category, 'pattern': pattern,
                'key': {k: _ing(v, profile) for k, v in key.items()}, 'result': {profile.result_key: result}}

    return {
        'hunters_lens': shaped([' S ', 'SGS', ' S '], {'S': f'{NS}:remnant_shard', 'G': 'minecraft:spyglass'},
                               f'{NS}:hunters_lens', 'equipment'),
        'brand_of_challenge': shaped(['SSS', 'SES', 'SSS'], {'S': f'{NS}:remnant_shard', 'E': 'minecraft:echo_shard'},
                                     f'{NS}:brand_of_challenge', 'misc'),
    }


REMNANT_BOSSES = ['ossukage', 'forgotten_watcher', 'hollow_sovereign', 'gravebound_warden', 'burial_guardian']


def tags(profile):
    return {
        ('tags/entity_type', 'bosses'): {'replace': False, 'values': [
            'minecraft:ender_dragon', 'minecraft:wither', 'minecraft:warden', 'minecraft:elder_guardian',
            {'id': '#c:bosses', 'required': False}, {'id': '#forge:bosses', 'required': False}]
            # Remnant Bosses: its bosses already have their own health, phases and boss bars.
            + [{'id': f'remnants:{boss}', 'required': False} for boss in REMNANT_BOSSES]},
        ('tags/entity_type', 'never_leveled'): {'replace': False, 'values': []},
        ('tags/entity_type', 'never_ranked'): {'replace': False, 'values': [
            'minecraft:vex', 'minecraft:silverfish', 'minecraft:endermite']},
        # Difficulty engine and event nights (3.1).
        ('tags/entity_type', 'ignores_difficulty'): {'replace': False, 'values': []},
        ('tags/entity_type', 'event_hunters'): {'replace': False, 'values': [
            'minecraft:zombie', 'minecraft:husk', 'minecraft:skeleton', 'minecraft:stray', 'minecraft:spider',
            'minecraft:vindicator', 'minecraft:pillager', 'minecraft:witch']},
        ('tags/entity_type', 'event_mini_bosses'): {'replace': False, 'values': [
            'minecraft:vindicator', 'minecraft:wither_skeleton', 'minecraft:evoker', 'minecraft:ravager']},
        ('tags/dimension_type', 'no_difficulty'): {'replace': False, 'values': []},
    }


# id, parent, icon, frame, hidden, criterion trigger
_ADVANCEMENTS = [
    ('root', None, 'remnant_shard', 'task', False, 'minecraft:tick'),
    ('know_thy_enemy', 'root', 'hunters_lens', 'task', False, 'minecraft:impossible'),
    ('taste_of_valor', 'root', 'remnant_shard', 'task', False, 'minecraft:impossible'),
    ('proven_hunter', 'root', 'iron_sword', 'task', False, 'minecraft:impossible'),
    ('champions_bane', 'proven_hunter', 'diamond_sword', 'goal', False, 'minecraft:impossible'),
    ('remnants_end', 'champions_bane', 'netherite_sword', 'challenge', False, 'minecraft:impossible'),
    ('punching_up', 'proven_hunter', 'crossbow', 'goal', False, 'minecraft:impossible'),
    ('nothing_left_to_hide', 'champions_bane', 'ender_eye', 'challenge', True, 'minecraft:impossible'),
    ('under_the_hunters_moon', 'proven_hunter', 'clock', 'goal', False, 'minecraft:impossible'),
    ('accept_the_challenge', 'taste_of_valor', 'brand_of_challenge', 'goal', False, 'minecraft:impossible'),
    ('see_the_dawn', 'proven_hunter', 'bell', 'goal', False, 'minecraft:impossible'),
    ('kindled', 'root', 'vital_ember', 'task', False, 'minecraft:impossible'),
]


def advancements(profile):
    out = {}
    for adv_id, parent, icon, frame, hidden, trigger in _ADVANCEMENTS:
        icon_id = icon if ':' in icon else (f'{NS}:{icon}' if icon in ITEMS else f'minecraft:{icon}')
        display = {
            'icon': {profile.icon_key: icon_id},
            # The fallback is what clients without the mod's language file read.
            'title': {'translate': f'advancements.{NS}.{adv_id}.title', 'fallback': ADVANCEMENTS[adv_id][0]},
            'description': {'translate': f'advancements.{NS}.{adv_id}.description', 'fallback': ADVANCEMENTS[adv_id][1]},
            'frame': frame,
            'show_toast': parent is not None,
            'announce_to_chat': parent is not None,
            'hidden': hidden,
        }
        if parent is None:
            display['background'] = profile.background
        adv = {'display': display, 'criteria': {'done': {'trigger': trigger}}, 'requirements': [['done']]}
        if parent:
            adv['parent'] = f'{NS}:{parent}'
        out[adv_id] = adv
    assert set(out) == set(ADVANCEMENTS)
    return out


def item_models():
    return {item: {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{item}'}} for item in ITEMS}


def item_definitions():
    return {item: {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{item}'}} for item in ITEMS}


def sounds_json():
    out = {}
    for event, (files, subtitle) in sounds.EVENTS.items():
        out[event] = {'subtitle': f'subtitles.{NS}.{subtitle}', 'sounds': [{'name': f'{NS}:{f}', 'stream': False} for f in files]}
    return out


def font_json():
    return icons.font_json(NS)
