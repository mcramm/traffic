(ns traffic.state)

(def app-state (atom {:update-freq 50
                      :options {:light-mode :auto
                                :north-south-green-time 11000
                                :east-west-green-time 12000}
                      :lights [{:pos [610 250]
                                :dim [13 30]
                                :pair :horizontal
                                :state :red}
                               {:pos [530 285]
                                :dim [13 30]
                                :pair :horizontal
                                :state :red}
                               {:pos [537 240]
                                :dim [35 13]
                                :pair :vertical
                                :state :red}
                               {:pos [575 315]
                                :dim [35 13]
                                :pair :vertical
                                :state :red}
                               ]
                      :cars []}))
