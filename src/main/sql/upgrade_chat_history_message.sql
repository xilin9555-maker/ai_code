-- 扩大历史消息字段容量，避免工程生成回复超过 TEXT 上限后保存失败。
-- MEDIUMTEXT 最多可保存约 16 MB，能够容纳多轮文件工具产生的完整回复。
alter table chat_history
    modify column message mediumtext not null comment '消息';
