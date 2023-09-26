/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

DO $$
DECLARE
    user_id UUID;
BEGIN

user_id := (SELECT id FROM bi_user WHERE name = 'system');

insert into species
(common_name, description, created_by, updated_by)
VALUES
('Sweet Potato', 'Ipomoea batatas, is an herbaceous perennial in the family Convolvulaceae grown for its edible storage roots', user_id, user_id),
('Blueberry', 'Blueberries are perennial flowering plants with blue or purple–colored berries.', user_id, user_id),
('Trout', 'Trout is the common name for a number of species of freshwater fish belonging to the genera Oncorhynchus, Salmo and Salvelinus, all of the subfamily Salmoninae of the family Salmonidae', user_id, user_id),
('Salmon', 'Salmon is a common food classified as an oily fish with a rich content of protein and omega-3 fatty acids.', user_id, user_id),
('Grape', 'A grape is a fruit, botanically a berry, of the deciduous woody vines of the flowering plant genus Vitis.', user_id, user_id),
('Alfalfa', 'Alfalfa, also called lucerne and called Medicago sativa in binomial nomenclature, is a perennial flowering plant in the legume family Fabaceae.', user_id, user_id);

insert into role
(domain, created_by, updated_by)
VALUES
('member', user_id, user_id),
('breeder', user_id, user_id);

insert into environment_type
(name, created_by, updated_by)
VALUES
('forest', user_id, user_id),
('field', user_id, user_id),
('nursery', user_id, user_id),
('tanks', user_id, user_id),
('open net pen', user_id, user_id);

insert into accessibility_option
(name, created_by, updated_by)
VALUES
('private', user_id, user_id),
('public', user_id, user_id);

insert into topography_option
(name, created_by, updated_by)
VALUES
('plateau', user_id, user_id),
('cirque', user_id, user_id),
('hill', user_id, user_id),
('valley', user_id, user_id);

insert into country
(name, alpha_2_code, alpha_3_code, created_by, updated_by)
VALUES
('Afghanistan', 'AF', 'AFG', user_id, user_id),
('Albania', 'AL', 'ALB', user_id, user_id),
('Algeria', 'DZ', 'DZA', user_id, user_id),
('American Samoa', 'AS', 'ASM', user_id, user_id),
('Andorra', 'AD', 'AND', user_id, user_id),
('Angola', 'AO', 'AGO', user_id, user_id),
('Anguilla', 'AI', 'AIA', user_id, user_id),
('Antarctica', 'AQ', 'ATA', user_id, user_id),
('Antigua and Barbuda', 'AG', 'ATG', user_id, user_id),
('Argentina', 'AR', 'ARG', user_id, user_id),
('Armenia', 'AM', 'ARM', user_id, user_id),
('Aruba', 'AW', 'ABW', user_id, user_id),
('Australia', 'AU', 'AUS', user_id, user_id),
('Austria', 'AT', 'AUT', user_id, user_id),
('Azerbaijan', 'AZ', 'AZE', user_id, user_id),
('Bahamas', 'BS', 'BHS', user_id, user_id),
('Bahrain', 'BH', 'BHR', user_id, user_id),
('Bangladesh', 'BD', 'BGD', user_id, user_id),
('Barbados', 'BB', 'BRB', user_id, user_id),
('Belarus', 'BY', 'BLR', user_id, user_id),
('Belgium', 'BE', 'BEL', user_id, user_id),
('Belize', 'BZ', 'BLZ', user_id, user_id),
('Benin', 'BJ', 'BEN', user_id, user_id),
('Bermuda', 'BM', 'BMU', user_id, user_id),
('Bhutan', 'BT', 'BTN', user_id, user_id),
('Bolivia', 'BO', 'BOL', user_id, user_id),
('Bonaire, Sint Eustatius and Saba', 'BQ', 'BES', user_id, user_id),
('Bosnia and Herzegovina', 'BA', 'BIH', user_id, user_id),
('Botswana', 'BW', 'BWA', user_id, user_id),
('Bouvet Island', 'BV', 'BVT', user_id, user_id),
('Brazil', 'BR', 'BRA', user_id, user_id),
('British Indian Ocean Territory', 'IO', 'IOT', user_id, user_id),
('Brunei Darussalam', 'BN', 'BRN', user_id, user_id),
('Bulgaria', 'BG', 'BGR', user_id, user_id),
('Burkina Faso', 'BF', 'BFA', user_id, user_id),
('Burundi', 'BI', 'BDI', user_id, user_id),
('Cabo Verde', 'CV', 'CPV', user_id, user_id),
('Cambodia', 'KH', 'KHM', user_id, user_id),
('Cameroon', 'CM', 'CMR', user_id, user_id),
('Canada', 'CA', 'CAN', user_id, user_id),
('Cayman Islands', 'KY', 'CYM', user_id, user_id),
('Central African Republic', 'CF', 'CAF', user_id, user_id),
('Chad', 'TD', 'TCD', user_id, user_id),
('Chile', 'CL', 'CHL', user_id, user_id),
('China', 'CN', 'CHN', user_id, user_id),
('Christmas Island', 'CX', 'CXR', user_id, user_id),
('Cocos (Keeling) Islands', 'CC', 'CCK', user_id, user_id),
('Colombia', 'CO', 'COL', user_id, user_id),
('Comoros', 'KM', 'COM', user_id, user_id),
('Congo (the Democratic Republic of the)', 'CD', 'COD', user_id, user_id),
('Congo', 'CG', 'COG', user_id, user_id),
('Cook Islands', 'CK', 'COK', user_id, user_id),
('Costa Rica', 'CR', 'CRI', user_id, user_id),
('Croatia', 'HR', 'HRV', user_id, user_id),
('Cuba', 'CU', 'CUB', user_id, user_id),
('Curaçao', 'CW', 'CUW', user_id, user_id),
('Cyprus', 'CY', 'CYP', user_id, user_id),
('Czechia', 'CZ', 'CZE', user_id, user_id),
('Côte d''Ivoire', 'CI', 'CIV', user_id, user_id),
('Denmark', 'DK', 'DNK', user_id, user_id),
('Djibouti', 'DJ', 'DJI', user_id, user_id),
('Dominica', 'DM', 'DMA', user_id, user_id),
('Dominican Republic', 'DO', 'DOM', user_id, user_id),
('Ecuador', 'EC', 'ECU', user_id, user_id),
('Egypt', 'EG', 'EGY', user_id, user_id),
('El Salvador', 'SV', 'SLV', user_id, user_id),
('Equatorial Guinea', 'GQ', 'GNQ', user_id, user_id),
('Eritrea', 'ER', 'ERI', user_id, user_id),
('Estonia', 'EE', 'EST', user_id, user_id),
('Eswatini', 'SZ', 'SWZ', user_id, user_id),
('Ethiopia', 'ET', 'ETH', user_id, user_id),
('Falkland Islands [Malvinas]', 'FK', 'FLK', user_id, user_id),
('Faroe Islands', 'FO', 'FRO', user_id, user_id),
('Fiji', 'FJ', 'FJI', user_id, user_id),
('Finland', 'FI', 'FIN', user_id, user_id),
('France', 'FR', 'FRA', user_id, user_id),
('French Guiana', 'GF', 'GUF', user_id, user_id),
('French Polynesia', 'PF', 'PYF', user_id, user_id),
('French Southern Territories', 'TF', 'ATF', user_id, user_id),
('Gabon', 'GA', 'GAB', user_id, user_id),
('Gambia', 'GM', 'GMB', user_id, user_id),
('Georgia', 'GE', 'GEO', user_id, user_id),
('Germany', 'DE', 'DEU', user_id, user_id),
('Ghana', 'GH', 'GHA', user_id, user_id),
('Gibraltar', 'GI', 'GIB', user_id, user_id),
('Greece', 'GR', 'GRC', user_id, user_id),
('Greenland', 'GL', 'GRL', user_id, user_id),
('Grenada', 'GD', 'GRD', user_id, user_id),
('Guadeloupe', 'GP', 'GLP', user_id, user_id),
('Guam', 'GU', 'GUM', user_id, user_id),
('Guatemala', 'GT', 'GTM', user_id, user_id),
('Guernsey', 'GG', 'GGY', user_id, user_id),
('Guinea', 'GN', 'GIN', user_id, user_id),
('Guinea-Bissau', 'GW', 'GNB', user_id, user_id),
('Guyana', 'GY', 'GUY', user_id, user_id),
('Haiti', 'HT', 'HTI', user_id, user_id),
('Heard Island and McDonald Islands', 'HM', 'HMD', user_id, user_id),
('Holy See', 'VA', 'VAT', user_id, user_id),
('Honduras', 'HN', 'HND', user_id, user_id),
('Hong Kong', 'HK', 'HKG', user_id, user_id),
('Hungary', 'HU', 'HUN', user_id, user_id),
('Iceland', 'IS', 'ISL', user_id, user_id),
('India', 'IN', 'IND', user_id, user_id),
('Indonesia', 'ID', 'IDN', user_id, user_id),
('Iran', 'IR', 'IRN', user_id, user_id),
('Iraq', 'IQ', 'IRQ', user_id, user_id),
('Ireland', 'IE', 'IRL', user_id, user_id),
('Isle of Man', 'IM', 'IMN', user_id, user_id),
('Israel', 'IL', 'ISR', user_id, user_id),
('Italy', 'IT', 'ITA', user_id, user_id),
('Jamaica', 'JM', 'JAM', user_id, user_id),
('Japan', 'JP', 'JPN', user_id, user_id),
('Jersey', 'JE', 'JEY', user_id, user_id),
('Jordan', 'JO', 'JOR', user_id, user_id),
('Kazakhstan', 'KZ', 'KAZ', user_id, user_id),
('Kenya', 'KE', 'KEN', user_id, user_id),
('Kiribati', 'KI', 'KIR', user_id, user_id),
('Korea', 'KR', 'KOR', user_id, user_id),
('Kuwait', 'KW', 'KWT', user_id, user_id),
('Kyrgyzstan', 'KG', 'KGZ', user_id, user_id),
('Lao People''s Democratic Republic', 'LA', 'LAO', user_id, user_id),
('Latvia', 'LV', 'LVA', user_id, user_id),
('Lebanon', 'LB', 'LBN', user_id, user_id),
('Lesotho', 'LS', 'LSO', user_id, user_id),
('Liberia', 'LR', 'LBR', user_id, user_id),
('Libya', 'LY', 'LBY', user_id, user_id),
('Liechtenstein', 'LI', 'LIE', user_id, user_id),
('Lithuania', 'LT', 'LTU', user_id, user_id),
('Luxembourg', 'LU', 'LUX', user_id, user_id),
('Macao', 'MO', 'MAC', user_id, user_id),
('Madagascar', 'MG', 'MDG', user_id, user_id),
('Malawi', 'MW', 'MWI', user_id, user_id),
('Malaysia', 'MY', 'MYS', user_id, user_id),
('Maldives', 'MV', 'MDV', user_id, user_id),
('Mali', 'ML', 'MLI', user_id, user_id),
('Malta', 'MT', 'MLT', user_id, user_id),
('Marshall Islands', 'MH', 'MHL', user_id, user_id),
('Martinique', 'MQ', 'MTQ', user_id, user_id),
('Mauritania', 'MR', 'MRT', user_id, user_id),
('Mauritius', 'MU', 'MUS', user_id, user_id),
('Mayotte', 'YT', 'MYT', user_id, user_id),
('Mexico', 'MX', 'MEX', user_id, user_id),
('Micronesia', 'FM', 'FSM', user_id, user_id),
('Moldova', 'MD', 'MDA', user_id, user_id),
('Monaco', 'MC', 'MCO', user_id, user_id),
('Mongolia', 'MN', 'MNG', user_id, user_id),
('Montenegro', 'ME', 'MNE', user_id, user_id),
('Montserrat', 'MS', 'MSR', user_id, user_id),
('Morocco', 'MA', 'MAR', user_id, user_id),
('Mozambique', 'MZ', 'MOZ', user_id, user_id),
('Myanmar', 'MM', 'MMR', user_id, user_id),
('Namibia', 'NA', 'NAM', user_id, user_id),
('Nauru', 'NR', 'NRU', user_id, user_id),
('Nepal', 'NP', 'NPL', user_id, user_id),
('Netherlands', 'NL', 'NLD', user_id, user_id),
('New Caledonia', 'NC', 'NCL', user_id, user_id),
('New Zealand', 'NZ', 'NZL', user_id, user_id),
('Nicaragua', 'NI', 'NIC', user_id, user_id),
('Niger', 'NE', 'NER', user_id, user_id),
('Nigeria', 'NG', 'NGA', user_id, user_id),
('Niue', 'NU', 'NIU', user_id, user_id),
('Norfolk Island', 'NF', 'NFK', user_id, user_id),
('Northern Mariana Islands', 'MP', 'MNP', user_id, user_id),
('Norway', 'NO', 'NOR', user_id, user_id),
('Oman', 'OM', 'OMN', user_id, user_id),
('Pakistan', 'PK', 'PAK', user_id, user_id),
('Palau', 'PW', 'PLW', user_id, user_id),
('Palestine, State of', 'PS', 'PSE', user_id, user_id),
('Panama', 'PA', 'PAN', user_id, user_id),
('Papua New Guinea', 'PG', 'PNG', user_id, user_id),
('Paraguay', 'PY', 'PRY', user_id, user_id),
('Peru', 'PE', 'PER', user_id, user_id),
('Philippines', 'PH', 'PHL', user_id, user_id),
('Pitcairn', 'PN', 'PCN', user_id, user_id),
('Poland', 'PL', 'POL', user_id, user_id),
('Portugal', 'PT', 'PRT', user_id, user_id),
('Puerto Rico', 'PR', 'PRI', user_id, user_id),
('Qatar', 'QA', 'QAT', user_id, user_id),
('Republic of North Macedonia', 'MK', 'MKD', user_id, user_id),
('Romania', 'RO', 'ROU', user_id, user_id),
('Russian Federation', 'RU', 'RUS', user_id, user_id),
('Rwanda', 'RW', 'RWA', user_id, user_id),
('Réunion', 'RE', 'REU', user_id, user_id),
('Saint Barthélemy', 'BL', 'BLM', user_id, user_id),
('Saint Helena, Ascension and Tristan da Cunha', 'SH', 'SHN', user_id, user_id),
('Saint Kitts and Nevis', 'KN', 'KNA', user_id, user_id),
('Saint Lucia', 'LC', 'LCA', user_id, user_id),
('Saint Martin', 'MF', 'MAF', user_id, user_id),
('Saint Pierre and Miquelon', 'PM', 'SPM', user_id, user_id),
('Saint Vincent and the Grenadines', 'VC', 'VCT', user_id, user_id),
('Samoa', 'WS', 'WSM', user_id, user_id),
('San Marino', 'SM', 'SMR', user_id, user_id),
('Sao Tome and Principe', 'ST', 'STP', user_id, user_id),
('Saudi Arabia', 'SA', 'SAU', user_id, user_id),
('Senegal', 'SN', 'SEN', user_id, user_id),
('Serbia', 'RS', 'SRB', user_id, user_id),
('Seychelles', 'SC', 'SYC', user_id, user_id),
('Sierra Leone', 'SL', 'SLE', user_id, user_id),
('Singapore', 'SG', 'SGP', user_id, user_id),
('Sint Maarten', 'SX', 'SXM', user_id, user_id),
('Slovakia', 'SK', 'SVK', user_id, user_id),
('Slovenia', 'SI', 'SVN', user_id, user_id),
('Solomon Islands', 'SB', 'SLB', user_id, user_id),
('Somalia', 'SO', 'SOM', user_id, user_id),
('South Africa', 'ZA', 'ZAF', user_id, user_id),
('South Georgia and the South Sandwich Islands', 'GS', 'SGS', user_id, user_id),
('South Sudan', 'SS', 'SSD', user_id, user_id),
('Spain', 'ES', 'ESP', user_id, user_id),
('Sri Lanka', 'LK', 'LKA', user_id, user_id),
('Sudan', 'SD', 'SDN', user_id, user_id),
('Suriname', 'SR', 'SUR', user_id, user_id),
('Svalbard and Jan Mayen', 'SJ', 'SJM', user_id, user_id),
('Sweden', 'SE', 'SWE', user_id, user_id),
('Switzerland', 'CH', 'CHE', user_id, user_id),
('Syrian Arab Republic', 'SY', 'SYR', user_id, user_id),
('Taiwan', 'TW', 'TWN', user_id, user_id),
('Tajikistan', 'TJ', 'TJK', user_id, user_id),
('Tanzania', 'TZ', 'TZA', user_id, user_id),
('Thailand', 'TH', 'THA', user_id, user_id),
('Timor-Leste', 'TL', 'TLS', user_id, user_id),
('Togo', 'TG', 'TGO', user_id, user_id),
('Tokelau', 'TK', 'TKL', user_id, user_id),
('Tonga', 'TO', 'TON', user_id, user_id),
('Trinidad and Tobago', 'TT', 'TTO', user_id, user_id),
('Tunisia', 'TN', 'TUN', user_id, user_id),
('Turkey', 'TR', 'TUR', user_id, user_id),
('Turkmenistan', 'TM', 'TKM', user_id, user_id),
('Turks and Caicos Islands', 'TC', 'TCA', user_id, user_id),
('Tuvalu', 'TV', 'TUV', user_id, user_id),
('Uganda', 'UG', 'UGA', user_id, user_id),
('Ukraine', 'UA', 'UKR', user_id, user_id),
('United Arab Emirates', 'AE', 'ARE', user_id, user_id),
('United Kingdom of Great Britain and Northern Ireland', 'GB', 'GBR', user_id, user_id),
('United States Minor Outlying Islands', 'UM', 'UMI', user_id, user_id),
('United States of America', 'US', 'USA', user_id, user_id),
('Uruguay', 'UY', 'URY', user_id, user_id),
('Uzbekistan', 'UZ', 'UZB', user_id, user_id),
('Vanuatu', 'VU', 'VUT', user_id, user_id),
('Venezuela (Bolivarian Republic of)', 'VE', 'VEN', user_id, user_id),
('Viet Nam', 'VN', 'VNM', user_id, user_id),
('Virgin Islands (British)', 'VG', 'VGB', user_id, user_id),
('Virgin Islands (U.S.)', 'VI', 'VIR', user_id, user_id),
('Wallis and Futuna', 'WF', 'WLF', user_id, user_id),
('Western Sahara', 'EH', 'ESH', user_id, user_id),
('Yemen', 'YE', 'YEM', user_id, user_id),
('Zambia', 'ZM', 'ZMB', user_id, user_id),
('Zimbabwe', 'ZW', 'ZWE', user_id, user_id),
('Åland Islands', 'AX', 'ALA', user_id, user_id);

END $$;