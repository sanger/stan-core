insert into tag_layout (id, name)
values (1, 'tag layout 1')
, (2, 'tag layout 2')
;
insert into tag_heading (id, tag_layout_id, heading_index, name)
values (10, 1, 0, 'Alpha')
    , (11, 1, 2, 'Gamma')
    , (12, 1, 1, 'Beta')
    , (20, 2, 0, 'Alpha')
;

insert into tag_entry (tag_heading_id, row_index, col_index, value)
values (10, 1, 1, 'AlphaA1')
    , (10, 1, 2, 'AlphaA2')
    , (11, 1, 1, 'GammaA1')
    , (11, 1, 2, 'GammaA2')
    , (12, 1, 2, 'BetaA2')
    , (12, 2, 1, 'BetaB1')
    , (20, 1, 1, 'OtherA1')
;

insert into reagent_plate_type_tag_layout (plate_type, tag_layout_id)
values ('Dual Index TT Set A', 1)
    , ('Dual Index TS Set A', 2)
;
