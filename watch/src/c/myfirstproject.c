#include <pebble.h>

#define MAX_FACE_ELEMENTS 4
#define MAX_ELEMENT_VALUE_LEN 256

#define KEY_FACE_COUNT 0
#define KEY_ELEM_TYPE(i) (1 + (i) * 2)
#define KEY_ELEM_VALUE(i) (2 + (i) * 2)

typedef struct {
    uint8_t type;
    char value[MAX_ELEMENT_VALUE_LEN];
    bool active;
} FaceElement;

typedef struct {
    uint8_t count;
    FaceElement elements[MAX_FACE_ELEMENTS];
} FaceLayout;

static Window *s_window;
static TextLayer *s_time_layer;
static TextLayer *s_face_layers[MAX_FACE_ELEMENTS];
static FaceLayout s_face_layout;

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

static void update_face_layout() {
    Layer *window_layer = window_get_root_layer(s_window);
    GRect bounds = layer_get_bounds(window_layer);

    int start_y = 65;
    int available_h = bounds.size.h - start_y;
    int active_count = s_face_layout.count;

    for (int i = 0; i < MAX_FACE_ELEMENTS; i++) {
        if (i < active_count && s_face_layout.elements[i].active) {
            int slot_h = available_h / active_count;
            int y = start_y + i * slot_h;
            layer_set_frame(text_layer_get_layer(s_face_layers[i]),
                            GRect(5, y, bounds.size.w - 10, slot_h));
            text_layer_set_text(s_face_layers[i],
                                s_face_layout.elements[i].value);
            layer_set_hidden(text_layer_get_layer(s_face_layers[i]), false);
        } else {
            layer_set_hidden(text_layer_get_layer(s_face_layers[i]), true);
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

    memset(&s_face_layout, 0, sizeof(s_face_layout));
    s_face_layout.count = count;

    for (int i = 0; i < count; i++) {
        Tuple *type_tuple = dict_find(iter, KEY_ELEM_TYPE(i));
        Tuple *value_tuple = dict_find(iter, KEY_ELEM_VALUE(i));

        if (type_tuple && value_tuple) {
            s_face_layout.elements[i].type = type_tuple->value->uint8;
            strncpy(s_face_layout.elements[i].value,
                    value_tuple->value->cstring,
                    MAX_ELEMENT_VALUE_LEN - 1);
            s_face_layout.elements[i].value[MAX_ELEMENT_VALUE_LEN - 1] = '\0';
            s_face_layout.elements[i].active = true;
        }
    }

    update_face_layout();
    vibes_short_pulse();
}

static void inbox_dropped_handler(AppMessageResult reason, void *context) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "Message dropped: %d", (int)reason);
}

static void init(void) {
    s_window = window_create();

    Layer *window_layer = window_get_root_layer(s_window);
    GRect bounds = layer_get_bounds(window_layer);

    // Time at top
    s_time_layer = text_layer_create(GRect(0, 10, bounds.size.w, 50));
    text_layer_set_background_color(s_time_layer, GColorClear);
    text_layer_set_text_color(s_time_layer, GColorBlack);
    text_layer_set_font(s_time_layer,
                        fonts_get_system_font(FONT_KEY_BITHAM_42_BOLD));
    text_layer_set_text_alignment(s_time_layer, GTextAlignmentCenter);
    layer_add_child(window_layer, text_layer_get_layer(s_time_layer));

    // Pre-allocate face element layers (hidden by default)
    memset(&s_face_layout, 0, sizeof(s_face_layout));
    for (int i = 0; i < MAX_FACE_ELEMENTS; i++) {
        s_face_layers[i] = text_layer_create(GRect(0, 0, 1, 1));
        text_layer_set_background_color(s_face_layers[i], GColorClear);
        text_layer_set_text_color(s_face_layers[i], GColorBlack);
        text_layer_set_font(s_face_layers[i],
                            fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD));
        text_layer_set_text_alignment(s_face_layers[i],
                                      GTextAlignmentCenter);
        text_layer_set_overflow_mode(s_face_layers[i],
                                     GTextOverflowModeWordWrap);
        layer_set_hidden(text_layer_get_layer(s_face_layers[i]), true);
        layer_add_child(window_layer, text_layer_get_layer(s_face_layers[i]));
    }

    window_stack_push(s_window, true);

    tick_timer_service_subscribe(MINUTE_UNIT, tick_handler);
    update_time();

    app_message_register_inbox_received(inbox_received_handler);
    app_message_register_inbox_dropped(inbox_dropped_handler);
    app_message_open(2048, 64);
}

static void deinit(void) {
    tick_timer_service_unsubscribe();
    for (int i = 0; i < MAX_FACE_ELEMENTS; i++) {
        text_layer_destroy(s_face_layers[i]);
    }
    text_layer_destroy(s_time_layer);
    window_destroy(s_window);
}

int main(void) {
    init();
    app_event_loop();
    deinit();
}
