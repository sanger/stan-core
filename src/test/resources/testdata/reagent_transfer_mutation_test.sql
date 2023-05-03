insert into tag_layout (id, name)
values (1, 'tag layout 1')
     , (2, 'tag layout 2')
;

insert into reagent_plate_type_tag_layout (plate_type, tag_layout_id)
values ('FFPE - Dual Index TS Set A', 1)
   , ('Fresh frozen - Dual Index TT Set A', 2)
;