(ns traffic.state)

(def app-state (atom {:update-freq 16
                      :options {:light-mode :auto
                                :north-south-green-time 11000
                                :east-west-green-time 12000}
                      :lights [{:pos [710 260]
                                :dim [13 50]
                                :pair :horizontal
                                :state :red}
                               {:pos [595 309]
                                :dim [13 50]
                                :pair :horizontal
                                :state :red}
                               {:pos [609 245]
                                :dim [50 13]
                                :pair :vertical
                                :state :red}
                               {:pos [660 358]
                                :dim [50 13]
                                :pair :vertical
                                :state :red}
                               ]
                      :cars []}))
