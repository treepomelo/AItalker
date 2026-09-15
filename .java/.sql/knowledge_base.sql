-- Knowledge base schema (Task 2)
CREATE TABLE IF NOT EXISTS knowledge_base (id varchar(64) primary key, name varchar(200) not null, status varchar(32) not null, created_at timestamp not null);
CREATE TABLE IF NOT EXISTS knowledge_document (id varchar(64) primary key, knowledge_base_id varchar(64) not null, name varchar(255), type varchar(32), content longtext, status varchar(32), error_message varchar(1000), created_at timestamp not null, index idx_kd_base(knowledge_base_id));
CREATE TABLE IF NOT EXISTS product (id bigint primary key auto_increment, name varchar(200) not null, description varchar(1000), created_at timestamp not null);
CREATE TABLE IF NOT EXISTS product_knowledge_rel (product_id bigint not null, knowledge_base_id varchar(64) not null, primary key(product_id,knowledge_base_id));
CREATE TABLE IF NOT EXISTS product_explanation (id varchar(64) primary key, product_id bigint not null, content longtext not null, status varchar(32), created_at timestamp not null, index idx_pe_product(product_id));
CREATE TABLE IF NOT EXISTS speech_task (id varchar(64) primary key, explanation_id varchar(64), status varchar(32), audio_url varchar(1000), error_message varchar(1000), created_at timestamp not null);
