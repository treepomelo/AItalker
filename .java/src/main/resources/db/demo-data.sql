-- Fictional fixtures. Existing records are never overwritten.
SET NAMES utf8mb4;
INSERT INTO product(id,name,description,created_at) VALUES(1001,'江南花窗 · 黄铜流苏书签','把园林花窗的几何线条，放进每天翻阅的书页。',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO product_profile(product_id,details) VALUES(1001,'{"price": "68.00", "material": "H62 黄铜，透明保护涂层；涤纶流苏", "specification": "主体 100 × 25 × 0.4 mm，流苏约 100 mm，单枚约 12 g", "packaging": "单枚书签、保护套、140 × 60 × 15 mm 抽屉纸盒", "care": "用柔软干布擦拭，避免水浸、汗液长时间接触和强力弯折；涂层磨损后可能自然氧化变色。", "highlights": ["镂空花窗纹样", "轻薄黄铜书签", "抽屉礼盒包装"], "isDemo": true, "disclaimer": "青禾文创为虚构测试品牌；规格、价格与售后均为演示设定，不代表真实在售商品，不宣称任何博物馆、景区或非遗机构授权。"}') ON DUPLICATE KEY UPDATE product_id=product_id;
INSERT INTO knowledge_base(id,name,status,created_at) VALUES('demo-kb-1001','江南花窗 · 黄铜流苏书签测试知识库','ACTIVE',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO product_knowledge_rel(product_id,knowledge_base_id) VALUES(1001,'demo-kb-1001') ON DUPLICATE KEY UPDATE product_id=product_id;
INSERT INTO knowledge_document(id,knowledge_base_id,name,type,content,status,created_at) VALUES('demo-doc-1001-1','demo-kb-1001','商品规格与包装','TEXT','商品：江南花窗 · 黄铜流苏书签。测试零售价：人民币 68.00 元。材质：H62 黄铜，透明保护涂层；涤纶流苏。规格：主体 100 × 25 × 0.4 mm，流苏约 100 mm，单枚约 12 g。包装：单枚书签、保护套、140 × 60 × 15 mm 抽屉纸盒。青禾文创为虚构测试品牌；规格、价格与售后均为演示设定，不代表真实在售商品，不宣称任何博物馆、景区或非遗机构授权。','COMPLETED',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO knowledge_document(id,knowledge_base_id,name,type,content,status,created_at) VALUES('demo-doc-1001-2','demo-kb-1001','设计灵感与使用场景','TEXT','设计以江南园林花窗的几何分格为灵感，将窗格疏密关系转化为镂空线条，适合作为阅读纪念或日常小礼物。产品为现代设计文具，不是古物复制品。
适合普通纸质书与手账；勿夹入过薄纸页。边缘做倒角，但不是儿童玩具，不建议幼童使用。
青禾文创为虚构测试品牌；规格、价格与售后均为演示设定，不代表真实在售商品，不宣称任何博物馆、景区或非遗机构授权。','COMPLETED',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO knowledge_document(id,knowledge_base_id,name,type,content,status,created_at) VALUES('demo-doc-1001-3','demo-kb-1001','养护与售后边界','TEXT','用柔软干布擦拭，避免水浸、汗液长时间接触和强力弯折；涂层磨损后可能自然氧化变色。
测试售后：7 日内未使用且配件包装完整可申请退货；质量问题由商家承担运费，非质量退货由买家承担。不含刻字定制。
青禾文创为虚构测试品牌；规格、价格与售后均为演示设定，不代表真实在售商品，不宣称任何博物馆、景区或非遗机构授权。','COMPLETED',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO product_explanation(id,product_id,content,status,created_at) VALUES('demo-explanation-1001',1001,'这枚江南花窗书签，把园林窗格的线条变成书页间的小风景。主体采用 H62 黄铜，配一根涤纶流苏，长约十厘米、宽两点五厘米，单枚约十二克。镂空花窗纹样配上抽屉纸盒，适合送给喜欢阅读和手账的朋友。平时用柔软干布擦拭，避免水浸和用力弯折；它是文具，不是儿童玩具。演示售价为六十八元，品牌与规格均为测试设定，并非真实在售或博物馆授权商品。','COMPLETED',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO explanation_detail(explanation_id,is_demo,scenario,tone,duration_seconds,sources) VALUES('demo-explanation-1001',true,'门店导购','自然亲切',60,'[{"id": "demo-doc-1001-1", "name": "商品规格与包装"}, {"id": "demo-doc-1001-2", "name": "设计灵感与使用场景"}, {"id": "demo-doc-1001-3", "name": "养护与售后边界"}]') ON DUPLICATE KEY UPDATE explanation_id=explanation_id;
INSERT INTO product(id,name,description,created_at) VALUES(1002,'宋韵月白 · 青瓷品茗杯','温润月白釉与简洁杯形，为日常茶席添一抹安静。',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO product_profile(product_id,details) VALUES(1002,'{"price": "128.00", "material": "瓷质杯体，月白色高温釉", "specification": "满杯约 150 mL，日常注水建议 100-120 mL；口径约 75 mm，高约 55 mm，约 130 g", "packaging": "单只茶杯、缓冲纸托、160 × 120 × 90 mm 礼盒", "care": "温水清洗，避免骤冷骤热，建议手洗，不建议微波炉或洗碗机使用。", "highlights": ["月白色釉面", "适合日常品茗", "单杯礼盒"], "isDemo": true, "disclaimer": "青禾文创为虚构测试品牌；规格、价格与售后均为演示设定，不代表真实在售商品，不宣称任何博物馆、景区或非遗机构授权。"}') ON DUPLICATE KEY UPDATE product_id=product_id;
INSERT INTO knowledge_base(id,name,status,created_at) VALUES('demo-kb-1002','宋韵月白 · 青瓷品茗杯测试知识库','ACTIVE',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO product_knowledge_rel(product_id,knowledge_base_id) VALUES(1002,'demo-kb-1002') ON DUPLICATE KEY UPDATE product_id=product_id;
INSERT INTO knowledge_document(id,knowledge_base_id,name,type,content,status,created_at) VALUES('demo-doc-1002-1','demo-kb-1002','商品规格与包装','TEXT','商品：宋韵月白 · 青瓷品茗杯。测试零售价：人民币 128.00 元。材质：瓷质杯体，月白色高温釉。规格：满杯约 150 mL，日常注水建议 100-120 mL；口径约 75 mm，高约 55 mm，约 130 g。包装：单只茶杯、缓冲纸托、160 × 120 × 90 mm 礼盒。青禾文创为虚构测试品牌；规格、价格与售后均为演示设定，不代表真实在售商品，不宣称任何博物馆、景区或非遗机构授权。','COMPLETED',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO knowledge_document(id,knowledge_base_id,name,type,content,status,created_at) VALUES('demo-doc-1002-2','demo-kb-1002','设计灵感与使用场景','TEXT','设计从宋代器物的简洁比例与含蓄审美汲取灵感。“宋韵”是设计主题，不代表宋代古物、特定窑口或非遗认证。
适合常见茶饮。无杯柄设计，杯壁可能烫手，应控制水温并小心取放。容量为测试设定值，存在少量测量差异。
青禾文创为虚构测试品牌；规格、价格与售后均为演示设定，不代表真实在售商品，不宣称任何博物馆、景区或非遗机构授权。','COMPLETED',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO knowledge_document(id,knowledge_base_id,name,type,content,status,created_at) VALUES('demo-doc-1002-3','demo-kb-1002','养护与售后边界','TEXT','温水清洗，避免骤冷骤热，建议手洗，不建议微波炉或洗碗机使用。
测试售后：运输破损可在签收后 48 小时内提供外包装与破损照片申请补发；未使用且包装完整支持 7 日退货，非质量退货运费由买家承担。不作保健功效承诺。
青禾文创为虚构测试品牌；规格、价格与售后均为演示设定，不代表真实在售商品，不宣称任何博物馆、景区或非遗机构授权。','COMPLETED',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO product_explanation(id,product_id,content,status,created_at) VALUES('demo-explanation-1002',1002,'如果你喜欢安静简洁的茶席，可以看看这只宋韵月白品茗杯。柔和的月白色釉面搭配简洁杯形，灵感来自宋代器物的含蓄审美。满杯容量约一百五十毫升，日常注水一百到一百二十毫升更方便端取，并配独立礼盒。使用时留意无柄杯的杯壁温度，避免骤冷骤热，建议手洗。演示售价为一百二十八元；宋韵指设计主题，不代表古物、特定窑口或非遗认证，以上均为测试商品资料。','COMPLETED',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO explanation_detail(explanation_id,is_demo,scenario,tone,duration_seconds,sources) VALUES('demo-explanation-1002',true,'门店导购','自然亲切',60,'[{"id": "demo-doc-1002-1", "name": "商品规格与包装"}, {"id": "demo-doc-1002-2", "name": "设计灵感与使用场景"}, {"id": "demo-doc-1002-3", "name": "养护与售后边界"}]') ON DUPLICATE KEY UPDATE explanation_id=explanation_id;
INSERT INTO product(id,name,description,created_at) VALUES(1003,'敦煌色谱 · 丝质小方巾','用赭红、石绿与沙金色组合，点亮日常穿搭。',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO product_profile(product_id,details) VALUES(1003,'{"price": "198.00", "material": "100% 桑蚕丝斜纹面料，数码印花，机器卷边", "specification": "约 53 × 53 cm，约 18 g；尺寸允许约 ±2 cm 测量差异", "packaging": "单条方巾、护理说明卡、180 × 180 × 25 mm 天地盖纸盒", "care": "30℃ 以下单独轻柔手洗，使用丝毛中性洗涤剂；勿浸泡、拧绞或暴晒，阴凉处晾干，低温隔布熨烫。", "highlights": ["桑蚕丝斜纹面料", "赭红石绿配色", "颈间与包柄多种系法"], "isDemo": true, "disclaimer": "青禾文创为虚构测试品牌；规格、价格与售后均为演示设定，不代表真实在售商品，不宣称任何博物馆、景区或非遗机构授权。"}') ON DUPLICATE KEY UPDATE product_id=product_id;
INSERT INTO knowledge_base(id,name,status,created_at) VALUES('demo-kb-1003','敦煌色谱 · 丝质小方巾测试知识库','ACTIVE',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO product_knowledge_rel(product_id,knowledge_base_id) VALUES(1003,'demo-kb-1003') ON DUPLICATE KEY UPDATE product_id=product_id;
INSERT INTO knowledge_document(id,knowledge_base_id,name,type,content,status,created_at) VALUES('demo-doc-1003-1','demo-kb-1003','商品规格与包装','TEXT','商品：敦煌色谱 · 丝质小方巾。测试零售价：人民币 198.00 元。材质：100% 桑蚕丝斜纹面料，数码印花，机器卷边。规格：约 53 × 53 cm，约 18 g；尺寸允许约 ±2 cm 测量差异。包装：单条方巾、护理说明卡、180 × 180 × 25 mm 天地盖纸盒。青禾文创为虚构测试品牌；规格、价格与售后均为演示设定，不代表真实在售商品，不宣称任何博物馆、景区或非遗机构授权。','COMPLETED',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO knowledge_document(id,knowledge_base_id,name,type,content,status,created_at) VALUES('demo-doc-1003-2','demo-kb-1003','设计灵感与使用场景','TEXT','采用赭红、石绿与沙金色组合，表达对敦煌壁画色彩氛围的现代想象。抽象几何纹样不直接复刻特定洞窟图像，不宣称敦煌研究院或其他机构授权。
可作颈部方巾、包柄装饰或发带。远离尖锐饰品和粘扣带，避免勾丝；屏幕显示与实物可能存在色差。
青禾文创为虚构测试品牌；规格、价格与售后均为演示设定，不代表真实在售商品，不宣称任何博物馆、景区或非遗机构授权。','COMPLETED',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO knowledge_document(id,knowledge_base_id,name,type,content,status,created_at) VALUES('demo-doc-1003-3','demo-kb-1003','养护与售后边界','TEXT','30℃ 以下单独轻柔手洗，使用丝毛中性洗涤剂；勿浸泡、拧绞或暴晒，阴凉处晾干，低温隔布熨烫。
测试售后：未下水、未使用且吊牌包装完整支持 7 日退货；非质量退货运费由买家承担。洗护不当或尖锐物勾丝不属于质量问题，不承诺防晒指数或护肤效果。
青禾文创为虚构测试品牌；规格、价格与售后均为演示设定，不代表真实在售商品，不宣称任何博物馆、景区或非遗机构授权。','COMPLETED',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO product_explanation(id,product_id,content,status,created_at) VALUES('demo-explanation-1003',1003,'这条敦煌色谱小方巾，用赭红、石绿和沙金色组成轻巧的配色练习。桑蚕丝斜纹面料约五十三厘米见方，可以系在颈间、点缀包柄或作为发带。抽象几何纹样适合简洁的日常衣着，礼盒附有护理卡。丝质面料需要温柔对待：低于三十度轻柔手洗，避免浸泡、拧绞和暴晒。这款虚构演示商品设定售价为一百九十八元，设计只表达配色灵感，不宣称任何敦煌相关机构授权。','COMPLETED',NOW()) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO explanation_detail(explanation_id,is_demo,scenario,tone,duration_seconds,sources) VALUES('demo-explanation-1003',true,'门店导购','自然亲切',60,'[{"id": "demo-doc-1003-1", "name": "商品规格与包装"}, {"id": "demo-doc-1003-2", "name": "设计灵感与使用场景"}, {"id": "demo-doc-1003-3", "name": "养护与售后边界"}]') ON DUPLICATE KEY UPDATE explanation_id=explanation_id;
