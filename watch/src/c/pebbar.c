#include <pebble.h>

#define MAX_FACE_ELEMENTS 4
#define MAX_ELEMENT_VALUE_LEN 256

#define KEY_FACE_COUNT 0
#define KEY_ELEM_TYPE(i) (1 + (i) * 3)
#define KEY_ELEM_VALUE(i) (2 + (i) * 3)
#define KEY_ELEM_ICON(i) (3 + (i) * 3)

#include "icons_generated.h"

#define MAX_ICON_NAME_LEN 32

typedef struct {
    uint8_t type;
    char value[MAX_ELEMENT_VALUE_LEN];
    char icon_name[MAX_ICON_NAME_LEN];
    bool active;
} FaceElement;

typedef struct {
    uint8_t count;
    FaceElement elements[MAX_FACE_ELEMENTS];
} FaceLayout;

static bool s_skip_duplicate_updates = true;

static Window *s_window;

// Time layer
static TextLayer *s_time_layer;

// Face layers
static TextLayer *s_face_layers[MAX_FACE_ELEMENTS];
static GBitmap *s_face_icons[MAX_FACE_ELEMENTS];
static BitmapLayer *s_face_icon_layers[MAX_FACE_ELEMENTS];
static FaceLayout s_face_layout;
static GFont s_face_font;

// ============================================================================
// Time Layer
// ============================================================================

static void update_time() {
    time_t temp = time(NULL);
    struct tm *tick_time = localtime(&temp);

    static char time_buffer[8];
    strftime(time_buffer, sizeof(time_buffer),
             clock_is_24h_style() ? "%H:%M" : "%I:%M", tick_time);

    text_layer_set_text(s_time_layer, time_buffer);
}

static void tick_handler(struct tm *tick_time, TimeUnits units_changed) {
    update_time();
}

static void init_time_layer(Layer *window_layer, GRect bounds) {
    s_time_layer = text_layer_create(GRect(0, 10, bounds.size.w, 50));
    text_layer_set_background_color(s_time_layer, GColorClear);
    text_layer_set_text_color(s_time_layer, GColorBlack);
    text_layer_set_font(s_time_layer,
                        fonts_get_system_font(FONT_KEY_BITHAM_42_BOLD));
    text_layer_set_text_alignment(s_time_layer, GTextAlignmentCenter);
    layer_add_child(window_layer, text_layer_get_layer(s_time_layer));

    tick_timer_service_subscribe(MINUTE_UNIT, tick_handler);
    update_time();
}

static void deinit_time_layer() {
    tick_timer_service_unsubscribe();
    text_layer_destroy(s_time_layer);
}

// ============================================================================
// Face Layers (dynamic content from Android)
// ============================================================================

#define ICON_SIZE 24
#define ICON_MARGIN 5

static void update_face_layout() {
    Layer *window_layer = window_get_root_layer(s_window);
    GRect bounds = layer_get_bounds(window_layer);

    int y = 65;

    for (int i = 0; i < MAX_FACE_ELEMENTS; i++) {
        // Clean up previous icon if any
        if (s_face_icons[i]) {
            gbitmap_destroy(s_face_icons[i]);
            s_face_icons[i] = NULL;
        }

        if (i < s_face_layout.count && s_face_layout.elements[i].active) {
            uint32_t icon_res = lookup_icon(s_face_layout.elements[i].icon_name);
            int text_x = ICON_MARGIN;
            int text_width = bounds.size.w - (ICON_MARGIN * 2);

            // Show icon if available
            if (icon_res != 0) {
                s_face_icons[i] = gbitmap_create_with_resource(icon_res);
                bitmap_layer_set_bitmap(s_face_icon_layers[i], s_face_icons[i]);
                layer_set_frame(bitmap_layer_get_layer(s_face_icon_layers[i]),
                                GRect(ICON_MARGIN, y, ICON_SIZE, ICON_SIZE));
                layer_set_hidden(bitmap_layer_get_layer(s_face_icon_layers[i]), false);

                text_x = ICON_MARGIN + ICON_SIZE + ICON_MARGIN;
                text_width = bounds.size.w - text_x - ICON_MARGIN;
            } else {
                layer_set_hidden(bitmap_layer_get_layer(s_face_icon_layers[i]), true);
            }

            GSize size = graphics_text_layout_get_content_size(
                s_face_layout.elements[i].value,
                s_face_font,
                GRect(0, 0, text_width, 1000),
                GTextOverflowModeWordWrap,
                GTextAlignmentLeft
            );
            int element_h = size.h;
            if (icon_res != 0 && element_h < ICON_SIZE) {
                element_h = ICON_SIZE;
            }

            // Vertically center text with icon (offset for font baseline)
            int text_y = y;
            if (icon_res != 0) {
                text_y = y - 2;  // Nudge up to align with icon center
            }

            layer_set_frame(text_layer_get_layer(s_face_layers[i]),
                            GRect(text_x, text_y, text_width, size.h));
            text_layer_set_text(s_face_layers[i],
                                s_face_layout.elements[i].value);
            layer_set_hidden(text_layer_get_layer(s_face_layers[i]), false);
            y += element_h;
        } else {
            layer_set_hidden(text_layer_get_layer(s_face_layers[i]), true);
            layer_set_hidden(bitmap_layer_get_layer(s_face_icon_layers[i]), true);
        }
    }
}

static void inbox_received_handler(DictionaryIterator *iter, void *context) {
    Tuple *count_tuple = dict_find(iter, KEY_FACE_COUNT);
    if (!count_tuple) {
        return;
    }

    uint8_t count = count_tuple->value->uint8;
    if (count > MAX_FACE_ELEMENTS) {
        count = MAX_FACE_ELEMENTS;
    }

    if (s_skip_duplicate_updates) {
        bool changed = (count != s_face_layout.count);

        if (!changed) {
            for (int i = 0; i < count && !changed; i++) {
                Tuple *type_tuple = dict_find(iter, KEY_ELEM_TYPE(i));
                Tuple *value_tuple = dict_find(iter, KEY_ELEM_VALUE(i));
                Tuple *icon_tuple = dict_find(iter, KEY_ELEM_ICON(i));

                if (type_tuple && value_tuple) {
                    if (s_face_layout.elements[i].type != type_tuple->value->uint8 ||
                        strcmp(s_face_layout.elements[i].value, value_tuple->value->cstring) != 0) {
                        changed = true;
                    }
                    const char *new_icon = icon_tuple ? icon_tuple->value->cstring : "";
                    if (strcmp(s_face_layout.elements[i].icon_name, new_icon) != 0) {
                        changed = true;
                    }
                }
            }
        }

        if (!changed) {
            return;
        }
    }

    memset(&s_face_layout, 0, sizeof(s_face_layout));
    s_face_layout.count = count;

    for (int i = 0; i < count; i++) {
        Tuple *type_tuple = dict_find(iter, KEY_ELEM_TYPE(i));
        Tuple *value_tuple = dict_find(iter, KEY_ELEM_VALUE(i));
        Tuple *icon_tuple = dict_find(iter, KEY_ELEM_ICON(i));

        if (type_tuple && value_tuple) {
            s_face_layout.elements[i].type = type_tuple->value->uint8;
            strncpy(s_face_layout.elements[i].value,
                    value_tuple->value->cstring,
                    MAX_ELEMENT_VALUE_LEN - 1);
            s_face_layout.elements[i].value[MAX_ELEMENT_VALUE_LEN - 1] = '\0';

            if (icon_tuple) {
                strncpy(s_face_layout.elements[i].icon_name,
                        icon_tuple->value->cstring,
                        MAX_ICON_NAME_LEN - 1);
                s_face_layout.elements[i].icon_name[MAX_ICON_NAME_LEN - 1] = '\0';
            }

            s_face_layout.elements[i].active = true;
        }
    }

    update_face_layout();
    vibes_short_pulse();
}

static void inbox_dropped_handler(AppMessageResult reason, void *context) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "Message dropped: %d", (int)reason);
}

static void init_face_layers(Layer *window_layer, GRect bounds) {
    memset(&s_face_layout, 0, sizeof(s_face_layout));
    memset(s_face_icons, 0, sizeof(s_face_icons));
    s_face_font = fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD);

    for (int i = 0; i < MAX_FACE_ELEMENTS; i++) {
        // Icon layer
        s_face_icon_layers[i] = bitmap_layer_create(GRect(0, 0, 1, 1));
        bitmap_layer_set_compositing_mode(s_face_icon_layers[i], GCompOpSet);
        layer_set_hidden(bitmap_layer_get_layer(s_face_icon_layers[i]), true);
        layer_add_child(window_layer, bitmap_layer_get_layer(s_face_icon_layers[i]));

        // Text layer
        s_face_layers[i] = text_layer_create(GRect(0, 0, 1, 1));
        text_layer_set_background_color(s_face_layers[i], GColorClear);
        text_layer_set_text_color(s_face_layers[i], GColorBlack);
        text_layer_set_font(s_face_layers[i], s_face_font);
        text_layer_set_text_alignment(s_face_layers[i], GTextAlignmentLeft);
        text_layer_set_overflow_mode(s_face_layers[i], GTextOverflowModeWordWrap);
        layer_set_hidden(text_layer_get_layer(s_face_layers[i]), true);
        layer_add_child(window_layer, text_layer_get_layer(s_face_layers[i]));
    }

    app_message_register_inbox_received(inbox_received_handler);
    app_message_register_inbox_dropped(inbox_dropped_handler);
    app_message_open(2048, 64);
}

static void deinit_face_layers() {
    for (int i = 0; i < MAX_FACE_ELEMENTS; i++) {
        if (s_face_icons[i]) {
            gbitmap_destroy(s_face_icons[i]);
        }
        bitmap_layer_destroy(s_face_icon_layers[i]);
        text_layer_destroy(s_face_layers[i]);
    }
}

// ============================================================================
// Main init/deinit
// ============================================================================

static void init(void) {
    s_window = window_create();

    Layer *window_layer = window_get_root_layer(s_window);
    GRect bounds = layer_get_bounds(window_layer);

    init_time_layer(window_layer, bounds);
    init_face_layers(window_layer, bounds);

    window_stack_push(s_window, true);
}

static void deinit(void) {
    deinit_time_layer();
    deinit_face_layers();

    window_destroy(s_window);
}

int main(void) {
    init();
    app_event_loop();
    deinit();
}
