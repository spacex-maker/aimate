-- Agent 生日：创建当天写入，年龄按真实日历推算；姓名/性别创建后不可改（应用层约束）
ALTER TABLE user_agents
  ADD COLUMN birthday DATE NULL COMMENT 'Agent 生日（创建日）' AFTER gender;

-- 存量：用 create_time 日期回填
UPDATE user_agents
SET birthday = DATE(create_time)
WHERE birthday IS NULL AND create_time IS NOT NULL;
